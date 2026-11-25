package cn.iocoder.yudao.module.product.controller.admin.spu.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 第三方平台商品采集预览 Request VO")
@Data
public class ProductSpuImportReqVO {

    @Schema(description = "第三方平台配置编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "请选择第三方平台配置")
    private Long platformId;

    @Schema(description = "第三方商品 URL 或商品 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://detail.1688.com/offer/123456.html")
    @NotBlank(message = "请输入商品来源 URL 或商品编号")
    private String sourceUrl;

    @Schema(description = "是否自动下载并转存图片到系统文件库（可选）", example = "false")
    private Boolean downloadImages;

}
