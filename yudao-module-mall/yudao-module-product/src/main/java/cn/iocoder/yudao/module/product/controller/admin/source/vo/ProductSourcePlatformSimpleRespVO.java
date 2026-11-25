package cn.iocoder.yudao.module.product.controller.admin.source.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 第三方货源平台精简 Response VO")
@Data
public class ProductSourcePlatformSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "配置名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "1688 开放平台")
    private String name;

    @Schema(description = "平台标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "1688")
    private String platformCode;

    @Schema(description = "采集方式：1=API接口，2=网页爬虫", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer fetchMode;

    @Schema(description = "平台接口网关或站点 URL")
    private String baseUrl;

}
