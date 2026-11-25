package cn.iocoder.yudao.module.promotion.service.diy;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.promotion.controller.admin.diy.vo.DiyImportRespVO;
import cn.iocoder.yudao.module.promotion.controller.admin.diy.vo.page.*;
import cn.iocoder.yudao.module.promotion.controller.admin.diy.vo.template.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.promotion.enums.ErrorCodeConstants.DIY_IMPORT_INVALID;

/** 复用文件归档与装修 Service，文件 IO 不占用装修数据事务。 */
@Service
public class DiyImportService {
    @Resource
    private FileApi fileApi;
    @Resource
    private DiyTemplateService templateService;
    @Resource
    private DiyPageService pageService;
    @Resource
    private TransactionTemplate transactionTemplate;
    private static final Pattern ASSET = Pattern.compile("asset://([^\\s\"'<>]+)");

    public DiyImportRespVO importPackage(MultipartFile file, String name, boolean template) throws IOException {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw exception(DIY_IMPORT_INVALID, "请选择不超过 10MB 的 ZIP 文件");
        }
        var pack = DiyPackageReader.read(file.getInputStream());
        ObjectNode manifest = pack.manifest();
        if (manifest.has("version") && manifest.path("version").asInt() != 1) {
            throw exception(DIY_IMPORT_INVALID, "不支持的主题包版本");
        }
        if (manifest.has("kind") && !manifest.path("kind").asText().equals(template ? "template" : "page")) {
            throw exception(DIY_IMPORT_INVALID, "主题包类型与当前导入入口不符");
        }
        for (String key : List.of("property", "home", "user")) {
            if (manifest.has(key) && !manifest.get(key).isObject()) {
                throw exception(DIY_IMPORT_INVALID, key + " 必须为装修配置对象");
            }
        }
        if (template && manifest.path("property").has("tabBar") && !manifest.path("property").path("tabBar").isObject()) {
            throw exception(DIY_IMPORT_INVALID, "模板 property 必须包含 tabBar；页面布局请放入 home/user");
        }
        if (manifest.hasNonNull("previewPicUrls") && (!manifest.path("previewPicUrls").isArray()
                || java.util.stream.StreamSupport.stream(manifest.path("previewPicUrls").spliterator(), false)
                .anyMatch(value -> !value.isTextual()))) {
            throw exception(DIY_IMPORT_INVALID, "previewPicUrls 必须为图片地址数组");
        }
        // 先校验全部图片引用，避免缺图的包导入到一半才失败。
        Map<String, String> references = new LinkedHashMap<>();
        pack.images().keySet().forEach(path -> references.put(path, path));
        rewrite(manifest, references);
        String directory = "diy/import/" + UUID.randomUUID();
        Map<String, String> urls = new LinkedHashMap<>();
        List<DiyImportRespVO.Asset> assets = new ArrayList<>();
        for (var entry : pack.images().entrySet()) {
            String path = entry.getKey();
            String originalName = path.substring(path.lastIndexOf('/') + 1);
            // 每张图独立目录，避免不同 ZIP 子目录下的同名图片互相覆盖。
            String url = fileApi.createFile(entry.getValue(), originalName, directory + "/" + assets.size(), null);
            urls.put(path, url);
            var asset = new DiyImportRespVO.Asset();
            asset.setPath(path);
            asset.setUrl(url);
            assets.add(asset);
        }
        ObjectNode resolved = (ObjectNode) rewrite(manifest, urls);
        Long id = transactionTemplate.execute(status -> template
                ? createTemplate(name, resolved, urls) : createPage(name, resolved.has("property") ? resolved.get("property") : imagePage(urls), urls));
        var response = new DiyImportRespVO();
        response.setId(id);
        response.setAssets(assets);
        return response;
    }

    private Long createTemplate(String name, ObjectNode manifest, Map<String, String> images) {
        var current = templateService.getUsedDiyTemplate();
        JsonNode basic = manifest.get("property");
        if (basic == null) basic = current != null ? JsonUtils.parseTree(current.getProperty()) : defaultBasic();
        if (basic == null || !basic.path("tabBar").isObject()) basic = defaultBasic();
        JsonNode user = manifest.get("user");
        if (user == null && current != null) {
            user = pageService.getDiyPageByTemplateId(current.getId()).stream().filter(page -> page.getName().equals("我的"))
                    .findFirst().map(page -> JsonUtils.parseTree(page.getProperty())).orElse(null);
        }
        if (user == null) user = emptyPage();
        var request = new DiyTemplateCreateReqVO();
        request.setName(name);
        request.setRemark(manifest.path("remark").asText("ZIP 导入，素材已归档到系统文件存储"));
        List<String> previews = new ArrayList<>();
        if (manifest.hasNonNull("previewPicUrls")) {
            manifest.path("previewPicUrls").forEach(value -> previews.add(value.asText()));
        } else {
            previews.addAll(images.values().stream().limit(3).toList());
        }
        request.setPreviewPicUrls(previews);
        Long id = templateService.createDiyTemplate(request);
        var property = new DiyTemplatePropertyUpdateRequestVO();
        property.setId(id);
        property.setProperty(JsonUtils.toJsonString(basic));
        templateService.updateDiyTemplateProperty(property);
        JsonNode home = manifest.has("home") ? manifest.get("home") : imagePage(images);
        for (var page : pageService.getDiyPageByTemplateId(id)) {
            updatePage(page.getId(), page.getName().equals("首页") ? home : user);
        }
        return id;
    }

    private Long createPage(String name, JsonNode property, Map<String, String> images) {
        var request = new DiyPageCreateReqVO();
        request.setName(name);
        request.setRemark("ZIP 导入，素材已归档到系统文件存储");
        request.setPreviewPicUrls(images.values().stream().limit(3).toList());
        Long id = pageService.createDiyPage(request);
        updatePage(id, property);
        return id;
    }

    private void updatePage(Long id, JsonNode property) {
        var request = new DiyPagePropertyUpdateRequestVO();
        request.setId(id);
        request.setProperty(JsonUtils.toJsonString(property));
        pageService.updateDiyPageProperty(request);
    }

    private ObjectNode emptyPage() {
        return (ObjectNode) JsonUtils.parseTree("""
                {"page":{"backgroundColor":"#f6f6f6"},"navigationBar":{"styleType":"normal","showType":"always","bgType":"color","bgColor":"#ffffff","mpCells":[],"otherCells":[]},"components":[]}
                """);
    }

    private ObjectNode imagePage(Map<String, String> images) {
        ObjectNode page = emptyPage();
        ArrayNode components = (ArrayNode) page.get("components");
        for (String url : images.values()) {
            ObjectNode component = components.addObject();
            component.put("id", "ImageBar");
            ObjectNode property = component.putObject("property");
            property.put("imgUrl", url);
            property.put("url", "");
            property.putObject("style").put("bgType", "color").put("bgColor", "#ffffff").put("marginBottom", 8);
        }
        return page;
    }

    private JsonNode defaultBasic() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode tabbar = root.putObject("tabBar");
        tabbar.put("theme", "orange");
        tabbar.putObject("style").put("bgType", "color").put("bgColor", "#ffffff").put("color", "#525252").put("activeColor", "#f37b1d");
        ArrayNode items = tabbar.putArray("items");
        String[] labels = {"首页", "分类", "购物车", "我的"};
        String[] paths = {"index", "category", "cart", "user"};
        for (int i = 0; i < labels.length; i++) {
            items.addObject().put("text", labels[i]).put("url", "/pages/index/" + paths[i]).put("iconUrl", "").put("activeIconUrl", "");
        }
        return root;
    }

    static JsonNode rewrite(JsonNode value, Map<String, String> urls) {
        if (value.isObject()) {
            ObjectNode result = value.deepCopy();
            value.fields().forEachRemaining(field -> result.set(field.getKey(), rewrite(field.getValue(), urls)));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            value.forEach(child -> result.add(rewrite(child, urls)));
            return result;
        }
        if (!value.isTextual()) return value;
        String text = value.asText();
        String path = text.startsWith("./") ? text.substring(2) : text;
        if (urls.containsKey(path)) return TextNode.valueOf(urls.get(path));
        if (path.startsWith("assets/")) throw exception(DIY_IMPORT_INVALID, "图片引用不存在：" + path);
        var matcher = ASSET.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String url = urls.get(matcher.group(1));
            if (url == null) throw exception(DIY_IMPORT_INVALID, "图片引用不存在：" + matcher.group(1));
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(url));
        }
        matcher.appendTail(result);
        return TextNode.valueOf(result.toString());
    }
}
