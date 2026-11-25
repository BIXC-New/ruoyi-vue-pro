package cn.iocoder.yudao.module.product.framework.source.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 第三方商品采集标准化结果（所有平台适配器 / 爬虫策略均输出该统一模型）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSourceNormalizedProduct {

    /**
     * 第三方商品编号（如 1688 offerId）
     */
    private String sourceProductId;

    /**
     * 商品来源 URL
     */
    private String sourceUrl;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 关键字
     */
    private String keyword;

    /**
     * 商品简介
     */
    private String introduction;

    /**
     * 商品详情 HTML
     */
    private String description;

    /**
     * 商品封面图
     */
    private String picUrl;

    /**
     * 商品轮播图
     */
    @Builder.Default
    private List<String> sliderPicUrls = new ArrayList<>();

    /**
     * 是否多规格：false=单规格，true=多规格
     */
    @Builder.Default
    private Boolean specType = Boolean.FALSE;

    /**
     * SKU 列表
     */
    @Builder.Default
    private List<SkuItem> skus = new ArrayList<>();

    /**
     * 原始报文摘要（便于排障，可选）
     */
    private String rawSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkuItem {

        /**
         * 第三方 SKU 编号
         */
        private String sourceSkuId;

        /**
         * SKU 名称
         */
        private String name;

        /**
         * 建议销售价（分）
         */
        private Integer price;

        /**
         * 市场价（分）
         */
        private Integer marketPrice;

        /**
         * 成本价/拿货价（分）
         */
        private Integer costPrice;

        /**
         * 库存
         */
        private Integer stock;

        /**
         * SKU 图片
         */
        private String picUrl;

        /**
         * 商品条码
         */
        private String barCode;

        /**
         * 属性列表
         */
        @Builder.Default
        private List<PropertyItem> properties = new ArrayList<>();

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PropertyItem {

        private String propertyName;

        private String valueName;

    }

}
