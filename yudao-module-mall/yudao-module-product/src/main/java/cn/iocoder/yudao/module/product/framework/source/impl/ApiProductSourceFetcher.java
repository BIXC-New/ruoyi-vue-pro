package cn.iocoder.yudao.module.product.framework.source.impl;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import cn.iocoder.yudao.module.product.enums.source.ProductSourceFetchModeEnum;
import cn.iocoder.yudao.module.product.framework.source.ProductSourceFetcher;
import cn.iocoder.yudao.module.product.framework.source.adapter.ProductSourceApiPayloadAdapter;
import cn.iocoder.yudao.module.product.framework.source.adapter.ProductSourceApiPayloadAdapterFactory;
import cn.iocoder.yudao.module.product.framework.source.model.ProductSourceNormalizedProduct;
import cn.iocoder.yudao.module.product.framework.source.util.SourcePayloadJsonUtils;
import cn.iocoder.yudao.module.product.framework.source.util.SourceUrlValidator;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOURCE_API_AUTH_INCOMPLETE;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOURCE_FETCH_FAILED;

/**
 * 第三方 API 模式采集器
 * <p>
 * 负责发起 HTTP 接口调用与基础鉴权头组装，具体报文解析交由 {@link ProductSourceApiPayloadAdapter} 处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiProductSourceFetcher implements ProductSourceFetcher {

    private final ProductSourceApiPayloadAdapterFactory adapterFactory;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public boolean supports(String platformCode, Integer fetchMode) {
        return Objects.equals(ProductSourceFetchModeEnum.API.getMode(), fetchMode);
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public ProductSourceNormalizedProduct fetch(ProductSourcePlatformDO config, String sourceUrlOrId) {
        if (StrUtil.isBlank(config.getBaseUrl())
                || (StrUtil.isBlank(config.getAppKey()) && StrUtil.isBlank(config.getAccessToken()))) {
            throw exception(SOURCE_API_AUTH_INCOMPLETE);
        }
        String normalizedSourceUrl = SourceUrlValidator.normalizeSourceUrl(
                config.getPlatformCode(), config.getBaseUrl(), sourceUrlOrId);
        String productId = SourceUrlValidator.extractProductId(config.getPlatformCode(), sourceUrlOrId);

        JsonNode extra = SourcePayloadJsonUtils.parseTreeQuietly(config.getExtraConfig());
        String requestUrl = buildRequestUrl(config, extra, normalizedSourceUrl, productId);
        SourceUrlValidator.validateExternalHttpUrl(requestUrl);

        String method = StrUtil.upperFirst(StrUtil.emptyToDefault(
                SourcePayloadJsonUtils.firstText(extra, "httpMethod"), "GET")).toUpperCase();
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");

        if (StrUtil.isNotBlank(config.getAccessToken())) {
            reqBuilder.header("Authorization", "Bearer " + config.getAccessToken().trim());
        }
        if (StrUtil.isNotBlank(config.getAppKey())) {
            reqBuilder.header("X-App-Key", config.getAppKey().trim());
        }
        appendExtraHeaders(reqBuilder, extra);

        if ("POST".equals(method)) {
            String bodyJson = String.format("{\"url\":\"%s\",\"productId\":\"%s\",\"appKey\":\"%s\"}",
                    escapeJson(normalizedSourceUrl), escapeJson(StrUtil.nullToEmpty(productId)),
                    escapeJson(StrUtil.nullToEmpty(config.getAppKey())));
            reqBuilder.header("Content-Type", "application/json");
            reqBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8));
        } else {
            reqBuilder.GET();
        }

        String rawPayload;
        try {
            HttpResponse<String> response = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw exception(SOURCE_FETCH_FAILED, "API HTTP 状态码 " + response.statusCode());
            }
            rawPayload = response.body();
        } catch (Exception ex) {
            if (ex instanceof cn.iocoder.yudao.framework.common.exception.ServiceException) {
                throw (cn.iocoder.yudao.framework.common.exception.ServiceException) ex;
            }
            log.warn("[fetch][API 请求失败 platformId={}, url={}]", config.getId(), requestUrl, ex);
            throw exception(SOURCE_FETCH_FAILED, ex.getMessage());
        }

        ProductSourceApiPayloadAdapter adapter = adapterFactory.getAdapter(config);
        return adapter.adapt(config, normalizedSourceUrl, rawPayload);
    }

    private String buildRequestUrl(ProductSourcePlatformDO config, JsonNode extra,
                                   String sourceUrl, String productId) {
        String base = StrUtil.removeSuffix(config.getBaseUrl().trim(), "/");
        String endpointPath = SourcePayloadJsonUtils.firstText(extra, "endpointPath");
        if (StrUtil.isNotBlank(endpointPath)) {
            base = base + (endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath);
        }
        String urlParam = StrUtil.emptyToDefault(SourcePayloadJsonUtils.firstText(extra, "urlParamName"), "url");
        String idParam = StrUtil.emptyToDefault(SourcePayloadJsonUtils.firstText(extra, "idParamName"), "offerId");

        StringBuilder sb = new StringBuilder(base);
        sb.append(base.contains("?") ? "&" : "?");
        sb.append(urlEncode(urlParam)).append("=").append(urlEncode(sourceUrl));
        if (StrUtil.isNotBlank(productId)) {
            sb.append("&").append(urlEncode(idParam)).append("=").append(urlEncode(productId));
        }
        return sb.toString();
    }

    private void appendExtraHeaders(HttpRequest.Builder builder, JsonNode extra) {
        JsonNode headers = SourcePayloadJsonUtils.getByPath(extra, "headers");
        if (headers != null && headers.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = headers.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (entry.getValue() != null && entry.getValue().isValueNode()) {
                    builder.header(entry.getKey(), entry.getValue().asText());
                }
            }
        }
    }

    private static String urlEncode(String val) {
        return URLEncoder.encode(StrUtil.nullToEmpty(val), StandardCharsets.UTF_8);
    }

    private static String escapeJson(String val) {
        return StrUtil.nullToEmpty(val).replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
