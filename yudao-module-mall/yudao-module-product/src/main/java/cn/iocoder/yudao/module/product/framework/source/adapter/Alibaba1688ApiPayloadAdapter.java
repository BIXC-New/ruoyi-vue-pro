package cn.iocoder.yudao.module.product.framework.source.adapter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import cn.iocoder.yudao.module.product.enums.source.ProductSourcePlatformEnum;
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
 * 1688 平台 API 报文适配器（骨架 + 常见开放平台字段适配）
 * <p>
 * 后续若对接具体 1688 官方开放平台接口或第三方聚合 API，只需在此适配器内补全/调整字段映射，
 * 或新增同平台不同 {@link #getCode()} 的适配器并在平台配置 extraConfig.adapterCode 中指定即可。
 */
@Component
public class Alibaba1688ApiPayloadAdapter implements ProductSourceApiPayloadAdapter {

    public static final String CODE = "alibaba_1688_api";

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public boolean supports(String platformCode, String adapterCode) {
        if (StrUtil.isNotBlank(adapterCode)) {
            return CODE.equalsIgnoreCase(adapterCode);
        }
        return ProductSourcePlatformEnum.ALIBABA_1688.getCode().equalsIgnoreCase(platformCode);
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public ProductSourceNormalizedProduct adapt(ProductSourcePlatformDO config, String sourceUrlOrId, String rawPayload) {
        JsonNode root = SourcePayloadJsonUtils.parseTreeQuietly(rawPayload);
        if (root == null) {
            throw exception(SOURCE_API_PAYLOAD_INVALID, "1688 API 响应不是合法 JSON");
        }
        // 检查常见网关错误字段
        String errorMessage = SourcePayloadJsonUtils.firstText(root, "error_message", "errorMsg", "message");
        JsonNode productNode = resolve1688ProductNode(root);
        if (productNode == null) {
            throw exception(SOURCE_API_PAYLOAD_INVALID,
                    StrUtil.firstNonBlank(errorMessage, "未在报文中找到 productInfo / result / data 节点"));
        }

        String name = SourcePayloadJsonUtils.firstText(productNode, "subject", "title", "name");
        if (StrUtil.isBlank(name)) {
            throw exception(SOURCE_API_PAYLOAD_INVALID,
                    StrUtil.firstNonBlank(errorMessage, "1688 报文中缺少商品标题 subject/title"));
        }

        String offerId = StrUtil.firstNonBlank(
                SourcePayloadJsonUtils.firstText(productNode, "offerId", "id", "productId"),
                SourceUrlValidator.extractProductId(ProductSourcePlatformEnum.ALIBABA_1688.getCode(), sourceUrlOrId));
        String sourceUrl = StrUtil.isNotBlank(offerId) && StrUtil.isNumeric(offerId)
                ? "https://detail.1688.com/offer/" + offerId + ".html"
                : SourceUrlValidator.normalizeSourceUrl(config.getPlatformCode(), config.getBaseUrl(), sourceUrlOrId);

        List<String> images = SourcePayloadJsonUtils.readStringList(productNode,
                "productImage.images", "image.images", "images", "sliderPicUrls");
        String picUrl = CollUtil.isNotEmpty(images) ? images.get(0)
                : SourcePayloadJsonUtils.normalizeImageUrl(SourcePayloadJsonUtils.firstText(productNode, "picUrl", "mainImg"));

        String description = StrUtil.firstNonBlank(
                SourcePayloadJsonUtils.firstText(productNode, "description", "detailUrl", "detailHtml"),
                "<p>" + name + "</p>");

        List<ProductSourceNormalizedProduct.SkuItem> skus = parse1688Skus(productNode, picUrl, name);
        boolean specType = skus.stream().anyMatch(sku -> CollUtil.isNotEmpty(sku.getProperties()));

        return ProductSourceNormalizedProduct.builder()
                .sourceProductId(offerId)
                .sourceUrl(sourceUrl)
                .name(StrUtil.subPre(name, 64))
                .keyword(StrUtil.subPre(name, 64))
                .introduction(StrUtil.subPre(name, 128))
                .description(description)
                .picUrl(picUrl)
                .sliderPicUrls(images)
                .specType(specType)
                .skus(skus)
                .rawSummary(StrUtil.subPre(rawPayload, 500))
                .build();
    }

    private JsonNode resolve1688ProductNode(JsonNode root) {
        for (String path : new String[]{"result.productInfo", "result", "data.productInfo", "data", "productInfo"}) {
            JsonNode candidate = SourcePayloadJsonUtils.getByPath(root, path);
            if (candidate != null && candidate.isObject()) {
                return candidate;
            }
        }
        return root.isObject() ? root : null;
    }

    private List<ProductSourceNormalizedProduct.SkuItem> parse1688Skus(JsonNode productNode, String defaultPicUrl, String spuName) {
        List<ProductSourceNormalizedProduct.SkuItem> skus = new ArrayList<>();
        JsonNode skuArray = SourcePayloadJsonUtils.getByPath(productNode, "productSkuInfos");
        if (skuArray == null || !skuArray.isArray()) {
            skuArray = SourcePayloadJsonUtils.getByPath(productNode, "skuInfos");
        }
        if (skuArray == null || !skuArray.isArray()) {
            skuArray = SourcePayloadJsonUtils.getByPath(productNode, "skus");
        }
        Integer fallbackWholesaleFen = SourcePayloadJsonUtils.firstPriceFen(productNode, false,
                "productSaleInfo.priceRangeList.0.price", "saleInfo.priceRanges.0.price", "referencePrice", "price");
        int defaultCost = fallbackWholesaleFen != null ? fallbackWholesaleFen : 0;

        if (skuArray != null && skuArray.isArray()) {
            for (JsonNode skuNode : skuArray) {
                Integer skuCostFen = SourcePayloadJsonUtils.firstPriceFen(skuNode, false,
                        "consignPrice", "price", "fenPrice", "jxhyPrice");
                int costFen = skuCostFen != null && skuCostFen > 0 ? skuCostFen : defaultCost;
                Integer stock = SourcePayloadJsonUtils.firstInt(skuNode, "amountOnSale", "stock", "quantity");

                List<ProductSourceNormalizedProduct.PropertyItem> props = new ArrayList<>();
                JsonNode attrArray = skuNode.get("skuAttributes");
                if (attrArray == null || !attrArray.isArray()) {
                    attrArray = skuNode.get("attributes");
                }
                String skuPic = defaultPicUrl;
                if (attrArray != null && attrArray.isArray()) {
                    for (JsonNode attr : attrArray) {
                        String pName = SourcePayloadJsonUtils.firstText(attr, "attributeName", "name", "propertyName");
                        String vName = SourcePayloadJsonUtils.firstText(attr, "value", "attributeValue", "valueName");
                        String attrImg = SourcePayloadJsonUtils.firstText(attr, "skuImageUrl", "imageUrl");
                        if (StrUtil.isNotBlank(attrImg)) {
                            skuPic = SourcePayloadJsonUtils.normalizeImageUrl(attrImg);
                        }
                        if (StrUtil.isNotBlank(pName) && StrUtil.isNotBlank(vName)) {
                            props.add(ProductSourceNormalizedProduct.PropertyItem.builder()
                                    .propertyName(pName)
                                    .valueName(vName)
                                    .build());
                        }
                    }
                }

                skus.add(ProductSourceNormalizedProduct.SkuItem.builder()
                        .sourceSkuId(SourcePayloadJsonUtils.firstText(skuNode, "skuId", "specId", "id"))
                        .name(spuName)
                        .costPrice(costFen)
                        .price(costFen)
                        .marketPrice(costFen)
                        .stock(stock != null ? Math.max(0, stock) : 100)
                        .picUrl(skuPic)
                        .barCode(StrUtil.emptyToDefault(SourcePayloadJsonUtils.firstText(skuNode, "cargoNumber", "barCode"), ""))
                        .properties(props)
                        .build());
            }
        }
        if (skus.isEmpty()) {
            skus.add(ProductSourceNormalizedProduct.SkuItem.builder()
                    .name(spuName)
                    .costPrice(defaultCost)
                    .price(defaultCost)
                    .marketPrice(defaultCost)
                    .stock(100)
                    .picUrl(defaultPicUrl)
                    .barCode("")
                    .build());
        }
        return skus;
    }

}
