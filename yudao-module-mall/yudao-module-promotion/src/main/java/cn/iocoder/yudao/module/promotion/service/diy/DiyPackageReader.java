package cn.iocoder.yudao.module.promotion.service.diy;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.promotion.enums.ErrorCodeConstants.DIY_IMPORT_INVALID;

/** 有界内存读取，不将 ZIP 路径解压到服务器文件系统。 */
final class DiyPackageReader {
    static final int MAX_FILE = 5 * 1024 * 1024;
    static final int MAX_TOTAL = 64 * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of("png", "image/png", "jpg", "image/jpeg",
            "jpeg", "image/jpeg", "gif", "image/gif", "webp", "image/webp", "svg", "image/svg+xml");
    record Contents(ObjectNode manifest, SortedMap<String, byte[]> images) { }

    static Contents read(InputStream input) {
        SortedMap<String, byte[]> images = new TreeMap<>();
        ObjectNode manifest = JsonUtils.parseObject("{}", ObjectNode.class);
        Set<String> names = new HashSet<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                // 兼容 Windows 压缩工具，统一路径后再执行目录穿越校验。
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("\\") || name.contains(":") || name.indexOf(0) >= 0
                        || Arrays.stream(name.split("/")).anyMatch(part -> part.equals("..") || part.equals("."))) {
                    throw exception(DIY_IMPORT_INVALID, "ZIP 包含非法路径");
                }
                if (!names.add(name) || names.size() > 200) throw exception(DIY_IMPORT_INVALID, "文件重名或超过 200 项");
                if (entry.isDirectory() || name.startsWith("__MACOSX/") || name.endsWith(".DS_Store")) continue;
                int limit = name.equals("diy.json") ? 1024 * 1024 : MAX_FILE;
                byte[] content = zip.readNBytes(limit + 1);
                total += content.length;
                if (content.length > limit || total > MAX_TOTAL) throw exception(DIY_IMPORT_INVALID, "文件或解压后总大小超限");
                if (name.equals("diy.json")) {
                    JsonNode json = JsonUtils.parseTree(content);
                    if (json == null || !json.isObject()) throw exception(DIY_IMPORT_INVALID, "diy.json 必须为对象");
                    manifest = (ObjectNode) json;
                } else {
                    String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                    if (!TYPES.containsKey(extension) || !TYPES.get(extension).equals(FileTypeUtils.getMineType(content, name))) {
                        throw exception(DIY_IMPORT_INVALID, "仅支持 PNG/JPEG/GIF/WebP/静态 SVG 图片：" + name);
                    }
                    if (extension.equals("svg")) validateSvg(content);
                    images.put(name, content);
                    if (images.size() > 100) throw exception(DIY_IMPORT_INVALID, "图片超过 100 张");
                }
            }
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw exception(DIY_IMPORT_INVALID, "ZIP 或装修配置无法解析");
        }
        if (images.isEmpty() && !manifest.has("property") && !manifest.has("home")) throw exception(DIY_IMPORT_INVALID, "包中没有图片或装修配置");
        return new Contents(manifest, images);
    }

    private static void validateSvg(byte[] content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
        Set<String> allowed = Set.of("svg", "g", "path", "rect", "circle", "ellipse", "line", "polyline", "polygon",
                "defs", "linearGradient", "radialGradient", "stop", "title", "desc", "use", "text", "tspan");
        if (!document.getDocumentElement().getTagName().equals("svg")) throw new IOException("Not SVG");
        var elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (!allowed.contains(element.getTagName())) throw new IOException("Unsafe SVG element");
            var attrs = element.getAttributes();
            for (int j = 0; j < attrs.getLength(); j++) {
                var attr = attrs.item(j);
                String key = attr.getNodeName().toLowerCase(Locale.ROOT);
                String value = attr.getNodeValue().trim();
                if (key.startsWith("on") || key.equals("style") || key.equals("xml:base") || value.contains("\\")
                        || ((key.equals("href") || key.equals("xlink:href")) && !value.startsWith("#"))
                        || (value.toLowerCase(Locale.ROOT).contains("url(") && !value.matches("url\\(#[a-zA-Z0-9_-]+\\)"))) {
                    throw new IOException("Unsafe SVG attribute");
                }
            }
        }
    }
}
