package cn.iocoder.yudao.module.product.framework.source.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;

import java.net.InetAddress;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOURCE_URL_INVALID;

/**
 * 第三方商品来源 URL 校验与 ID 提取工具（包含基础 SSRF 防护）
 */
public final class SourceUrlValidator {

    private static final Pattern ALIBABA_1688_OFFER_ID_PATTERN =
            Pattern.compile("/offer/(\\d+)\\.html|(?:offerId|id)=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("^\\d{5,24}$");

    private SourceUrlValidator() {
    }

    /**
     * 校验 HTTP/HTTPS URL 语法与基础字面量安全（不发起 DNS 解析，适用于保存表单校验）
     */
    public static URI validateHttpUrlSyntax(String rawUrl) {
        if (StrUtil.isBlank(rawUrl)) {
            throw exception(SOURCE_URL_INVALID);
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception ex) {
            throw exception(SOURCE_URL_INVALID);
        }
        String scheme = StrUtil.emptyToDefault(uri.getScheme(), "").toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw exception(SOURCE_URL_INVALID);
        }
        String host = uri.getHost();
        if (StrUtil.isBlank(host)) {
            throw exception(SOURCE_URL_INVALID);
        }
        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost) || lowerHost.endsWith(".local")
                || lowerHost.startsWith("127.") || lowerHost.startsWith("10.")
                || lowerHost.startsWith("192.168.") || lowerHost.startsWith("169.254.")
                || "::1".equals(lowerHost) || "[::1]".equals(lowerHost)) {
            throw exception(SOURCE_URL_INVALID);
        }
        return uri;
    }

    /**
     * 校验 HTTP/HTTPS URL 格式并通过 DNS 解析拦截内网/本地回环地址（用于后端发起外网请求前 SSRF 防护）
     */
    public static URI validateExternalHttpUrl(String rawUrl) {
        URI uri = validateHttpUrlSyntax(rawUrl);
        String host = uri.getHost();
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isAnyLocalAddress()
                        || addr.isLoopbackAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw exception(SOURCE_URL_INVALID);
                }
            }
        } catch (Exception ex) {
            if (ex instanceof cn.iocoder.yudao.framework.common.exception.ServiceException) {
                throw (cn.iocoder.yudao.framework.common.exception.ServiceException) ex;
            }
            throw exception(SOURCE_URL_INVALID);
        }
        return uri;
    }

    /**
     * 若输入的是纯商品 ID，结合平台 baseUrl 补全成可访问的来源 URL；否则校验并返回标准 URL
     */
    public static String normalizeSourceUrl(String platformCode, String baseUrl, String sourceUrlOrId) {
        String trimmed = StrUtil.trim(sourceUrlOrId);
        if (StrUtil.isBlank(trimmed)) {
            throw exception(SOURCE_URL_INVALID);
        }
        if (NUMERIC_ID_PATTERN.matcher(trimmed).matches() && "1688".equalsIgnoreCase(platformCode)) {
            return "https://detail.1688.com/offer/" + trimmed + ".html";
        }
        if (!StrUtil.startWithAnyIgnoreCase(trimmed, "http://", "https://")) {
            if (StrUtil.isNotBlank(baseUrl) && StrUtil.startWithAnyIgnoreCase(baseUrl.trim(), "http://", "https://")) {
                String prefix = StrUtil.removeSuffix(baseUrl.trim(), "/");
                String path = StrUtil.startWith(trimmed, "/") ? trimmed : "/" + trimmed;
                trimmed = prefix + path;
            }
        }
        validateExternalHttpUrl(trimmed);
        return trimmed;
    }

    /**
     * 从来源 URL 或纯 ID 提取第三方商品 ID
     */
    public static String extractProductId(String platformCode, String sourceUrlOrId) {
        String trimmed = StrUtil.trim(sourceUrlOrId);
        if (StrUtil.isBlank(trimmed)) {
            return null;
        }
        if (NUMERIC_ID_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        if ("1688".equalsIgnoreCase(platformCode) || StrUtil.containsIgnoreCase(trimmed, "1688.com")) {
            Matcher matcher = ALIBABA_1688_OFFER_ID_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                return StrUtil.firstNonBlank(matcher.group(1), matcher.group(2));
            }
        }
        return DigestUtil.md5Hex(trimmed).substring(0, 16);
    }

    /**
     * 元/字符串价格转分（失败返回 0）
     */
    public static int parseYuanToFen(String rawPrice) {
        if (StrUtil.isBlank(rawPrice)) {
            return 0;
        }
        // 处理价格区间如 "12.50-18.00"，默认取最小起批价
        String cleaned = rawPrice.replaceAll("[^0-9.\\-]", " ").trim();
        if (StrUtil.isBlank(cleaned)) {
            return 0;
        }
        String first = cleaned.split("[\\s\\-]+")[0];
        try {
            double yuan = Double.parseDouble(first);
            return Math.max(0, (int) Math.round(yuan * 100));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

}
