package cn.iocoder.yudao.module.product.framework.source.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import cn.iocoder.yudao.module.product.enums.source.ProductSourceFetchModeEnum;
import cn.iocoder.yudao.module.product.framework.source.ProductSourceFetcher;
import cn.iocoder.yudao.module.product.framework.source.model.ProductSourceNormalizedProduct;
import cn.iocoder.yudao.module.product.framework.source.util.SourcePayloadJsonUtils;
import cn.iocoder.yudao.module.product.framework.source.util.SourceUrlValidator;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOURCE_FETCH_FAILED;

/**
 * 网页爬虫模式采集器（支持 1688 及通用商品详情页抓取）
 * <p>
 * 支持从配置读取 Cookie、自定义请求头及 CSS 选择器（extraConfig），
 * 并自动结合 JSON-LD、OpenGraph Meta 标签与页面正文进行兜底提取。
 */
@Slf4j
@Component
public class CrawlerProductSourceFetcher implements ProductSourceFetcher {

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    @Override
    public boolean supports(String platformCode, Integer fetchMode) {
        return Objects.equals(ProductSourceFetchModeEnum.CRAWLER.getMode(), fetchMode);
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public ProductSourceNormalizedProduct fetch(ProductSourcePlatformDO config, String sourceUrlOrId) {
        String sourceUrl = SourceUrlValidator.normalizeSourceUrl(
                config.getPlatformCode(), config.getBaseUrl(), sourceUrlOrId);
        String sourceProductId = SourceUrlValidator.extractProductId(config.getPlatformCode(), sourceUrl);

        JsonNode extra = SourcePayloadJsonUtils.parseTreeQuietly(config.getExtraConfig());
        Document doc = fetchDocument(config, extra, sourceUrl);
        return parseDocument(config, extra, doc, sourceUrl, sourceProductId);
    }

    protected Document fetchDocument(ProductSourcePlatformDO config, JsonNode extra, String sourceUrl) {
        try {
            String userAgent = StrUtil.emptyToDefault(
                    SourcePayloadJsonUtils.firstText(extra, "userAgent"), DEFAULT_USER_AGENT);
            int timeoutMs = Objects.requireNonNullElse(
                    SourcePayloadJsonUtils.firstInt(extra, "timeoutMs"), 15000);
            Connection conn = Jsoup.connect(sourceUrl)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .header("Accept-Language", "zh-CN,zh;q=0.9");
            if (StrUtil.isNotBlank(config.getCookie())) {
                conn.header("Cookie", config.getCookie().trim());
            }
            JsonNode headers = SourcePayloadJsonUtils.getByPath(extra, "headers");
            if (headers != null && headers.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = headers.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    if (entry.getValue() != null && entry.getValue().isValueNode()) {
                        conn.header(entry.getKey(), entry.getValue().asText());
                    }
                }
            }
            return conn.get();
        } catch (Exception ex) {
            log.warn("[fetchDocument][爬虫抓取失败 platformId={}, url={}]", config.getId(), sourceUrl, ex);
            throw exception(SOURCE_FETCH_FAILED, "页面抓取失败：" + ex.getMessage());
        }
    }

    /**
     * 解析 HTML 文档为标准商品模型（拆分为独立方法，方便单元测试直接传入本地 HTML 验证解析逻辑）
     */
    public ProductSourceNormalizedProduct parseDocument(ProductSourcePlatformDO config, JsonNode extra,
                                                        Document doc, String sourceUrl, String sourceProductId) {
        if (isBlockedByAntiBot(doc)) {
            throw exception(SOURCE_FETCH_FAILED, "目标站点触发反爬/登录校验，请在平台配置中更新有效 Cookie 后重试");
        }

        JsonNode jsonLd = extractJsonLdProduct(doc);
        String title = StrUtil.firstNonBlank(
                selectText(doc, SourcePayloadJsonUtils.firstText(extra, "titleSelector")),
                SourcePayloadJsonUtils.firstText(jsonLd, "name"),
                attrContent(doc, "meta[property=og:title]", "content"),
                selectText(doc, "h1"),
                doc.title());
        if (StrUtil.isBlank(title)) {
            throw exception(SOURCE_FETCH_FAILED, "未能从页面解析出商品名称");
        }

        String introduction = StrUtil.firstNonBlank(
                selectText(doc, SourcePayloadJsonUtils.firstText(extra, "introSelector")),
                SourcePayloadJsonUtils.firstText(jsonLd, "description"),
                attrContent(doc, "meta[property=og:description]", "content"),
                attrContent(doc, "meta[name=description]", "content"),
                title);
        String keyword = StrUtil.firstNonBlank(
                selectText(doc, SourcePayloadJsonUtils.firstText(extra, "keywordSelector")),
                attrContent(doc, "meta[name=keywords]", "content"),
                title);

        List<String> images = extractImages(doc, extra, jsonLd);
        String picUrl = CollUtil.isNotEmpty(images) ? images.get(0) : "";

        String priceRaw = StrUtil.firstNonBlank(
                selectText(doc, SourcePayloadJsonUtils.firstText(extra, "priceSelector")),
                SourcePayloadJsonUtils.firstText(jsonLd, "offers.price", "offers.lowPrice"),
                attrContent(doc, "meta[property=product:price:amount]", "content"));
        int costPriceFen = SourceUrlValidator.parseYuanToFen(priceRaw);

        String descHtml = extractDescriptionHtml(doc, extra, introduction);

        ProductSourceNormalizedProduct.SkuItem singleSku = ProductSourceNormalizedProduct.SkuItem.builder()
                .name(StrUtil.subPre(title, 64))
                .costPrice(costPriceFen)
                .price(costPriceFen)
                .marketPrice(costPriceFen)
                .stock(100)
                .picUrl(picUrl)
                .barCode("")
                .build();

        return ProductSourceNormalizedProduct.builder()
                .sourceProductId(sourceProductId)
                .sourceUrl(sourceUrl)
                .name(StrUtil.subPre(title.trim(), 64))
                .keyword(StrUtil.subPre(keyword.trim(), 128))
                .introduction(StrUtil.subPre(introduction.trim(), 128))
                .description(descHtml)
                .picUrl(picUrl)
                .sliderPicUrls(images)
                .specType(Boolean.FALSE)
                .skus(List.of(singleSku))
                .build();
    }

    private boolean isBlockedByAntiBot(Document doc) {
        String title = StrUtil.nullToEmpty(doc.title()).toLowerCase();
        String bodyText = doc.body() != null ? StrUtil.subPre(doc.body().text(), 300).toLowerCase() : "";
        return (title.contains("验证码") || title.contains("captcha") || title.contains("安全验证"))
                && bodyText.length() < 200;
    }

    private JsonNode extractJsonLdProduct(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            JsonNode node = SourcePayloadJsonUtils.parseTreeQuietly(script.data());
            if (node == null) {
                continue;
            }
            if (node.isArray() && !node.isEmpty()) {
                node = node.get(0);
            }
            String type = SourcePayloadJsonUtils.firstText(node, "@type");
            if (StrUtil.isBlank(type) || StrUtil.containsIgnoreCase(type, "Product")) {
                return node;
            }
        }
        return null;
    }

    private List<String> extractImages(Document doc, JsonNode extra, JsonNode jsonLd) {
        List<String> images = new ArrayList<>();
        String imageSelector = SourcePayloadJsonUtils.firstText(extra, "imageSelector");
        if (StrUtil.isNotBlank(imageSelector)) {
            for (Element img : doc.select(imageSelector)) {
                addImageIfValid(images, StrUtil.firstNonBlank(img.absUrl("src"), img.attr("data-src"), img.attr("src")));
            }
        }
        if (CollUtil.isEmpty(images) && jsonLd != null) {
            for (String imgUrl : SourcePayloadJsonUtils.readStringList(jsonLd, "image")) {
                addImageIfValid(images, imgUrl);
            }
        }
        String ogImg = attrContent(doc, "meta[property=og:image]", "content");
        addImageIfValid(images, ogImg);
        if (images.isEmpty()) {
            for (Element img : doc.select("img[src]")) {
                addImageIfValid(images, StrUtil.firstNonBlank(img.absUrl("src"), img.attr("src")));
                if (images.size() >= 6) {
                    break;
                }
            }
        }
        return images;
    }

    private String extractDescriptionHtml(Document doc, JsonNode extra, String fallbackText) {
        String descSelector = SourcePayloadJsonUtils.firstText(extra, "descSelector");
        if (StrUtil.isNotBlank(descSelector)) {
            Element el = doc.selectFirst(descSelector);
            if (el != null && StrUtil.isNotBlank(el.html())) {
                return el.html();
            }
        }
        return "<p>" + StrUtil.emptyToDefault(fallbackText, "") + "</p>";
    }

    private static void addImageIfValid(List<String> list, String rawUrl) {
        if (StrUtil.isBlank(rawUrl)) {
            return;
        }
        String normalized = SourcePayloadJsonUtils.normalizeImageUrl(rawUrl);
        if (StrUtil.startWithAnyIgnoreCase(normalized, "http://", "https://") && !list.contains(normalized)) {
            list.add(normalized);
        }
    }

    private static String selectText(Document doc, String selector) {
        if (StrUtil.isBlank(selector)) {
            return null;
        }
        Element el = doc.selectFirst(selector);
        return el != null ? StrUtil.trim(el.text()) : null;
    }

    private static String attrContent(Document doc, String selector, String attr) {
        Element el = doc.selectFirst(selector);
        return el != null ? StrUtil.trim(el.attr(attr)) : null;
    }

}
