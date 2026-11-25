package cn.iocoder.yudao.module.product.controller.admin.source.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 第三方货源平台配置 Response VO")
@Data
public class ProductSourcePlatformRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "配置名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "1688 开放平台")
    private String name;

    @Schema(description = "平台标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "1688")
    private String platformCode;

    @Schema(description = "采集方式：1=API接口，2=网页爬虫", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer fetchMode;

    @Schema(description = "平台接口网关或站点 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String baseUrl;

    @Schema(description = "API AppKey")
    private String appKey;

    @Schema(description = "API AppSecret（脱敏）")
    private String appSecret;

    @Schema(description = "授权 AccessToken（脱敏）")
    private String accessToken;

    @Schema(description = "刷新 Token（脱敏）")
    private String refreshToken;

    @Schema(description = "Token 过期时间")
    private LocalDateTime tokenExpireTime;

    @Schema(description = "爬虫模式登录 Cookie（脱敏）")
    private String cookie;

    @Schema(description = "扩展配置 JSON")
    private String extraConfig;

    @Schema(description = "状态：0=开启，1=关闭", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
