package cn.iocoder.yudao.module.product.framework.source.util;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 第三方报文 JSON 读取辅助工具（避免各适配器重复编写空指针与路径解析逻辑）
 */
public final class SourcePayloadJsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SourcePayloadJsonUtils() {
    }

    public static JsonNode parseTreeQuietly(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 按点分路径读取节点，例如 "result.productInfo.subject"
     */
    public static JsonNode getByPath(JsonNode root, String path) {
        if (root == null || StrUtil.isBlank(path)) {
            return null;
        }
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || current.isNull() || current.isMissingNode()) {
                return null;
            }
            if (current.isArray() && StrUtil.isNumeric(segment)) {
                int idx = Integer.parseInt(segment);
                current = idx >= 0 && idx < current.size() ? current.get(idx) : null;
            } else {
                current = current.get(segment);
            }
        }
        return current != null && !current.isMissingNode() && !current.isNull() ? current : null;
    }

    public static String firstText(JsonNode node, String... paths) {
        if (node == null || paths == null) {
            return null;
        }
        for (String path : paths) {
            JsonNode target = getByPath(node, path);
            if (target != null && target.isValueNode()) {
                String text = StrUtil.trim(target.asText());
                if (StrUtil.isNotBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    public static Integer firstInt(JsonNode node, String... paths) {
        String text = firstText(node, paths);
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(text));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 读取价格并转换为分（默认认为小数或配置为元时按元转分，若值本身为整数且 >= 100 且字段名为 *Fen 则直接返回）
     */
    public static Integer firstPriceFen(JsonNode node, boolean priceInFen, String... paths) {
        if (node == null || paths == null) {
            return null;
        }
        for (String path : paths) {
            JsonNode target = getByPath(node, path);
            if (target == null || !target.isValueNode()) {
                continue;
            }
            String text = StrUtil.trim(target.asText());
            if (StrUtil.isBlank(text)) {
                continue;
            }
            if (priceInFen || StrUtil.containsIgnoreCase(path, "fen")) {
                try {
                    return Math.max(0, (int) Math.round(Double.parseDouble(text)));
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
            return SourceUrlValidator.parseYuanToFen(text);
        }
        return null;
    }

    public static List<String> readStringList(JsonNode node, String... paths) {
        List<String> result = new ArrayList<>();
        if (node == null || paths == null) {
            return result;
        }
        for (String path : paths) {
            JsonNode target = getByPath(node, path);
            if (target == null) {
                continue;
            }
            if (target.isArray()) {
                for (JsonNode item : target) {
                    String val = item.isValueNode() ? item.asText() : firstText(item, "url", "src", "picUrl", "imageUrl");
                    if (StrUtil.isNotBlank(val) && !result.contains(val)) {
                        result.add(normalizeImageUrl(val));
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            } else if (target.isTextual()) {
                for (String part : target.asText().split(",")) {
                    if (StrUtil.isNotBlank(part)) {
                        result.add(normalizeImageUrl(part.trim()));
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        return result;
    }

    public static String normalizeImageUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        return trimmed;
    }

}
