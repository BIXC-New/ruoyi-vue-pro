package cn.iocoder.yudao.module.product.controller.admin.spu.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 第三方平台商品采集预览 Response VO")
@Data
public class ProductSpuImportRespVO {

    @Schema(description = "商品来源类型：0=自有商品，1=第三方平台", example = "1")
    private Integer sourceType;

    @Schema(description = "第三方平台配置编号", example = "1")
    private Long sourcePlatformId;

    @Schema(description = "第三方平台标识", example = "1688")
    private String sourcePlatformCode;

    @Schema(description = "第三方平台商品编号", example = "6823948123")
    private String sourceProductId;

    @Schema(description = "商品来源 URL", example = "https://detail.1688.com/offer/6823948123.html")
    private String sourceUrl;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "关键字")
    private String keyword;

    @Schema(description = "商品简介")
    private String introduction;

    @Schema(description = "商品详情 HTML")
    private String description;

    @Schema(description = "商品封面图")
    private String picUrl;

    @Schema(description = "商品轮播图")
    private List<String> sliderPicUrls;

    @Schema(description = "规格类型：false=单规格，true=多规格")
    private Boolean specType;

    @Schema(description = "SKU 数组")
    private List<ProductSkuSaveReqVO> skus;

    @Schema(description = "若当前租户已存在同 sourceProductId 商品，返回其 SPU 编号作为防重提醒")
    private Long duplicateSpuId;

    @Schema(description = "已存在的重复商品名称")
    private String duplicateSpuName;

}
