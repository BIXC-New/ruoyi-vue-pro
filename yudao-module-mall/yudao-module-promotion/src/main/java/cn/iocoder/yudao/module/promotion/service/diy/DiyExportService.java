package cn.iocoder.yudao.module.promotion.service.diy;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileResourceRespDTO;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.iocoder.yudao.module.promotion.dal.dataobject.diy.DiyTemplateDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.promotion.enums.ErrorCodeConstants.*;

@Service
public class DiyExportService {

    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<> )]+");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "svg");

    @Resource
    private DiyTemplateService templateService;
    @Resource
    private DiyPageService pageService;
    @Resource
    private FileApi fileApi;

    public byte[] exportTemplate(Long id) throws Exception {
        DiyTemplateDO template = templateService.getDiyTemplate(id);
        if (template == null) {
            throw exception(DIY_TEMPLATE_NOT_EXISTS);
        }
        ObjectNode manifest = JsonNodeFactory.instance.objectNode();
        manifest.put("version", 1).put("kind", "template").put("name", template.getName());
        manifest.put("remark", template.getRemark());
        manifest.set("previewPicUrls", JsonUtils.parseTree(JsonUtils.toJsonString(template.getPreviewPicUrls())));
        JsonNode basic = JsonUtils.parseTree(template.getProperty());
        manifest.set("property", basic == null || basic.isNull() ? JsonNodeFactory.instance.objectNode() : basic);
        for (var page : pageService.getDiyPageByTemplateId(id)) {
            if (page.getName().equals("首页")) {
                manifest.set("home", JsonUtils.parseTree(page.getProperty()));
            }
            if (page.getName().equals("我的")) {
                manifest.set("user", JsonUtils.parseTree(page.getProperty()));
            }
        }
        if (JsonUtils.toJsonString(manifest).getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            throw exception(DIY_EXPORT_INVALID, "装修配置超过 1MB");
        }
        Set<String> urls = new LinkedHashSet<>();
        collect(manifest, urls);
        List<FileResourceRespDTO> resources;
        try {
            resources = fileApi.getImageResources(urls);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw exception(DIY_EXPORT_INVALID, "图片资源读取失败，请检查文件管理中的素材");
        }
        Map<String, String> replacements = new LinkedHashMap<>();
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (var resource : resources) {
            String suffix = FileTypeUtils.getExtension(resource.getType());
            String extension = suffix == null ? "" : suffix.replaceFirst("^\\.", "");
            if (!IMAGE_EXTENSIONS.contains(extension)) {
                throw exception(DIY_EXPORT_INVALID, "包含当前导入器不支持的图片格式：" + resource.getName());
            }
            String path = "assets/" + assets.size() + "." + extension;
            assets.put(path, resource.getContent());
            replacements.put(resource.getUrl(), "asset://" + path);
        }
        ObjectNode resolved = (ObjectNode) rewrite(manifest, replacements);
        ArrayNode external = resolved.putArray("externalAssets");
        urls.stream().filter(url -> !replacements.containsKey(url)).forEach(external::add);
        byte[] json = JsonUtils.toJsonString(resolved).getBytes(StandardCharsets.UTF_8);
        if (json.length > 1024 * 1024) {
            throw exception(DIY_EXPORT_INVALID, "装修配置超过 1MB");
        }
        // 同一读取器验证导出包，确保可再次导入，且已有 SVG 也遵循导入安全规则。
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            write(zip, "diy.json", json);
            for (var asset : assets.entrySet()) {
                write(zip, asset.getKey(), asset.getValue());
            }
        }
        if (bytes.size() > 10 * 1024 * 1024) {
            throw exception(DIY_EXPORT_INVALID, "ZIP 超过可导入的 10MB 限制");
        }
        DiyPackageReader.read(new java.io.ByteArrayInputStream(bytes.toByteArray()));
        return bytes.toByteArray();
    }

    private void write(ZipOutputStream zip, String path, byte[] data) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(data);
        zip.closeEntry();
    }

    private void collect(JsonNode node, Set<String> urls) {
        if (node.isContainerNode()) {
            node.forEach(child -> collect(child, urls));
            return;
        }
        if (node.isTextual()) {
            String text = node.asText();
            var matcher = URL.matcher(text);
            while (matcher.find()) {
                urls.add(matcher.group());
            }
            if (urls.size() > 2000) {
                throw exception(DIY_EXPORT_INVALID, "资源引用超过 2000 个");
            }
        }
    }

    private JsonNode rewrite(JsonNode node, Map<String, String> replacements) {
        if (node.isObject()) {
            ObjectNode result = ((ObjectNode) node).deepCopy();
            node.fields().forEachRemaining(field -> result.set(field.getKey(), rewrite(field.getValue(), replacements)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> result.add(rewrite(child, replacements)));
            return result;
        }
        if (!node.isTextual()) {
            return node;
        }
        String text = node.asText();
        // 长地址优先，避免一个 URL 是另一个 URL 前缀时替换出错。
        var entries = replacements.entrySet().stream().sorted(Comparator.comparingInt(
                (Map.Entry<String, String> entry) -> entry.getKey().length()).reversed()).toList();
        for (var entry : entries) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return TextNode.valueOf(text);
    }
}
