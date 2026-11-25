package cn.iocoder.yudao.module.product.framework.source.adapter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import cn.iocoder.yudao.module.product.framework.source.model.ProductSourceNormalizedProduct;
import cn.iocoder.yudao.module.product.framework.source.util.SourcePayloadJsonUtils;
import cn.iocoder.yudao.module.product.framework.source.util.SourceUrlValidator;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOURCE_API_PAYLOAD_INVALID;

/**
 * 通用可配置 JSON 报文适配器（兜底适配器）
 * <p>
 * 支持通过平台配置 extraConfig.fieldMapping 指定自定义 JSON 路径映射，
 * 也兼容常见通用字段（data/result/item 下的 title/name/subject、price/costPrice、skus 等）。
 */
@Component
public class DefaultJsonProductApiPayloadAdapter implements ProductSourceApiPayloadAdapter {

    public static final String CODE = "default_json";

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public boolean supports(String platformCode, String adapterCode) {
        return StrUtil.isBlank(adapterCode) || CODE.equalsIgnoreCase(adapterCode);
    }

    @Override
    public int getOrder() {
        // 优先级低于专用平台适配器，作为兜底使用
        return 1000;
    }

    @Override
    public ProductSourceNormalizedProduct adapt(ProductSourcePlatformDO config, String sourceUrlOrId, String rawPayload) {
        JsonNode root = SourcePayloadJsonUtils.parseTreeQuietly(rawPayload);
        if (root == null) {
            throw exception(SOURCE_API_PAYLOAD_INVALID, "响应不是合法的 JSON 格式");
        }
        JsonNode extra = SourcePayloadJsonUtils.parseTreeQuietly(config.getExtraConfig());
        JsonNode fieldMapping = SourcePayloadJsonUtils.getByPath(extra, "fieldMapping");
        boolean priceInFen = "fen".equalsIgnoreCase(SourcePayloadJsonUtils.firstText(extra, "priceUnit"));

        JsonNode dataNode = resolveDataRoot(root, fieldMapping);
        String name = readMappedOrDefault(dataNode, fieldMapping, "name", "name", "title", "subject", "productName");
        if (StrUtil.isBlank(name)) {
            throw exception(SOURCE_API_PAYLOAD_INVALID, "未识别到商品名称字段");
        }

        String sourceUrl = SourceUrlValidator.normalizeSourceUrl(config.getPlatformCode(), config.getBaseUrl(), sourceUrlOrId);
        String sourceProductId = StrUtil.firstNonBlank(
                readMappedOrDefault(dataNode, fieldMapping, "productId", "offerId", "productId", "itemId", "id"),
                SourceUrlValidator.extractProductId(config.getPlatformCode(), sourceUrlOrId));

        List<String> sliderPicUrls = readMappedListOrDefault(dataNode, fieldMapping, "sliderPicUrls",
                "sliderPicUrls", "images", "picUrls", "imageList", "gallery");
        String picUrl = SourcePayloadJsonUtils.normalizeImageUrl(StrUtil.firstNonBlank(
                readMappedOrDefault(dataNode, fieldMapping, "picUrl", "picUrl", "mainImg", "coverUrl", "image"),
                CollUtil.isNotEmpty(sliderPicUrls) ? sliderPicUrls.get(0) : ""));
        if (CollUtil.isEmpty(sliderPicUrls) && StrUtil.isNotBlank(picUrl)) {
            sliderPicUrls = new ArrayList<>(List.of(picUrl));
        }

        String keyword = StrUtil.firstNonBlank(
                readMappedOrDefault(dataNode, fieldMapping, "keyword", "keyword", "keywords"),
                StrUtil.subPre(name, 64));
        String introduction = StrUtil.firstNonBlank(
                readMappedOrDefault(dataNode, fieldMapping, "introduction", "introduction", "subTitle", "summary"),
                StrUtil.subPre(name, 128));
        String description = StrUtil.firstNonBlank(
                readMappedOrDefault(dataNode, fieldMapping, "description", "description", "detailHtml", "detail", "desc"),
                "<p>" + name + "</p>");

        List<ProductSourceNormalizedProduct.SkuItem> skus = parseSkus(dataNode, fieldMapping, priceInFen, picUrl, name);
        boolean specType = skus.stream().anyMatch(sku -> CollUtil.isNotEmpty(sku.getProperties()));

        return ProductSourceNormalizedProduct.builder()
                .sourceProductId(sourceProductId)
                .sourceUrl(sourceUrl)
                .name(StrUtil.subPre(name, 64))
                .keyword(StrUtil.subPre(keyword, 128))
                .introduction(StrUtil.subPre(introduction, 128))
                .description(description)
                .picUrl(picUrl)
                .sliderPicUrls(sliderPicUrls)
                .specType(specType)
                .skus(skus)
                .rawSummary(StrUtil.subPre(rawPayload, 500))
                .build();
    }

    private JsonNode resolveDataRoot(JsonNode root, JsonNode fieldMapping) {
        String rootPath = SourcePayloadJsonUtils.firstText(fieldMapping, "root");
        if (StrUtil.isNotBlank(rootPath)) {
            JsonNode mapped = SourcePayloadJsonUtils.getByPath(root, rootPath);
            if (mapped != null) {
                return mapped;
            }
        }
        for (String candidate : new String[]{"data", "result", "item", "productInfo"}) {
            JsonNode child = root.get(candidate);
            if (child != null && child.isObject()) {
                return child;
            }
        }
        return root;
    }

    private String readMappedOrDefault(JsonNode dataNode, JsonNode fieldMapping, String mappingKey, String... defaults) {
        String customPath = SourcePayloadJsonUtils.firstText(fieldMapping, mappingKey);
        if (StrUtil.isNotBlank(customPath)) {
            String mapped = SourcePayloadJsonUtils.firstText(dataNode, customPath);
            if (StrUtil.isNotBlank(mapped)) {
                return mapped;
            }
        }
        return SourcePayloadJsonUtils.firstText(dataNode, defaults);
    }

    private List<String> readMappedListOrDefault(JsonNode dataNode, JsonNode fieldMapping, String mappingKey, String... defaults) {
        String customPath = SourcePayloadJsonUtils.firstText(fieldMapping, mappingKey);
        if (StrUtil.isNotBlank(customPath)) {
            List<String> mapped = SourcePayloadJsonUtils.readStringList(dataNode, customPath);
            if (CollUtil.isNotEmpty(mapped)) {
                return mapped;
            }
        }
        return SourcePayloadJsonUtils.readStringList(dataNode, defaults);
    }

    private List<ProductSourceNormalizedProduct.SkuItem> parseSkus(JsonNode dataNode, JsonNode fieldMapping,
                                                                   boolean priceInFen, String defaultPicUrl, String spuName) {
        List<ProductSourceNormalizedProduct.SkuItem> skus = new ArrayList<>();
        String skusPath = StrUtil.firstNonBlank(SourcePayloadJsonUtils.firstText(fieldMapping, "skus"), "skus");
        JsonNode skuArray = SourcePayloadJsonUtils.getByPath(dataNode, skusPath);
        if (skuArray == null || !skuArray.isArray()) {
            skuArray = SourcePayloadJsonUtils.getByPath(dataNode, "skuList");
        }
        if (skuArray != null && skuArray.isArray()) {
            for (JsonNode skuNode : skuArray) {
                Integer costPrice = SourcePayloadJsonUtils.firstPriceFen(skuNode, priceInFen, "costPrice", "wholesalePrice", "price");
                Integer price = SourcePayloadJsonUtils.firstPriceFen(skuNode, priceInFen, "price", "salePrice", "retailPrice");
                Integer marketPrice = SourcePayloadJsonUtils.firstPriceFen(skuNode, priceInFen, "marketPrice", "originalPrice");
                int finalCost = costPrice != null ? costPrice : (price != null ? price : 0);
                int finalPrice = price != null && price > 0 ? price : finalCost;
                int finalMarket = marketPrice != null && marketPrice > 0 ? marketPrice : finalPrice;
                Integer stock = SourcePayloadJsonUtils.firstInt(skuNode, "stock", "quantity", "amountOnSale");
                String skuPic = SourcePayloadJsonUtils.normalizeImageUrl(StrUtil.firstNonBlank(
                        SourcePayloadJsonUtils.firstText(skuNode, "picUrl", "imageUrl", "img"), defaultPicUrl));

                List<ProductSourceNormalizedProduct.PropertyItem> props = new ArrayList<>();
                JsonNode propArray = skuNode.get("properties");
                if (propArray == null || !propArray.isArray()) {
                    propArray = skuNode.get("attributes");
                }
                if (propArray != null && propArray.isArray()) {
                    for (JsonNode propNode : propArray) {
                        String pName = SourcePayloadJsonUtils.firstText(propNode, "propertyName", "attributeName", "name", "key");
                        String vName = SourcePayloadJsonUtils.firstText(propNode, "valueName", "attributeValue", "value", "val");
                        if (StrUtil.isNotBlank(pName) && StrUtil.isNotBlank(vName)) {
                            props.add(ProductSourceNormalizedProduct.PropertyItem.builder()
                                    .propertyName(pName)
                                    .valueName(vName)
                                    .build());
                        }
                    }
                }

                skus.add(ProductSourceNormalizedProduct.SkuItem.builder()
                        .sourceSkuId(SourcePayloadJsonUtils.firstText(skuNode, "sourceSkuId", "skuId", "specId", "id"))
                        .name(StrUtil.firstNonBlank(SourcePayloadJsonUtils.firstText(skuNode, "name"), spuName))
                        .costPrice(finalCost)
                        .price(finalPrice)
                        .marketPrice(finalMarket)
                        .stock(stock != null ? Math.max(0, stock) : 100)
                        .picUrl(skuPic)
                        .barCode(StrUtil.emptyToDefault(SourcePayloadJsonUtils.firstText(skuNode, "barCode"), ""))
                        .properties(props)
                        .build());
            }
        }
        if (skus.isEmpty()) {
            Integer costPrice = SourcePayloadJsonUtils.firstPriceFen(dataNode, priceInFen, "costPrice", "wholesalePrice", "price");
            Integer price = SourcePayloadJsonUtils.firstPriceFen(dataNode, priceInFen, "price", "salePrice");
            int finalCost = costPrice != null ? costPrice : (price != null ? price : 0);
            int finalPrice = price != null && price > 0 ? price : finalCost;
            Integer stock = SourcePayloadJsonUtils.firstInt(dataNode, "stock", "quantity");
            skus.add(ProductSourceNormalizedProduct.SkuItem.builder()
                    .name(spuName)
                    .costPrice(finalCost)
                    .price(finalPrice)
                    .marketPrice(finalPrice)
                    .stock(stock != null ? Math.max(0, stock) : 100)
                    .picUrl(defaultPicUrl)
                    .barCode("")
                    .build());
        }
        return skus;
    }

}
