package cn.iocoder.yudao.module.product.controller.admin.source.vo;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.product.enums.source.ProductSourceFetchModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 第三方货源平台配置新增/修改 Request VO")
@Data
public class ProductSourcePlatformSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "配置名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "1688 开放平台")
    @NotBlank(message = "配置名称不能为空")
    private String name;

    @Schema(description = "平台标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "1688")
    @NotBlank(message = "平台标识不能为空")
    private String platformCode;

    @Schema(description = "采集方式：1=API接口，2=网页爬虫", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "采集方式不能为空")
    @InEnum(value = ProductSourceFetchModeEnum.class, message = "采集方式必须是 {value}")
    private Integer fetchMode;

    @Schema(description = "平台接口网关或站点 URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://gw.open.1688.com/openapi")
    @NotBlank(message = "平台 URL 不能为空")
    @URL(message = "平台 URL 格式不正确")
    private String baseUrl;

    @Schema(description = "API AppKey", example = "123456")
    private String appKey;

    @Schema(description = "API AppSecret", example = "secret")
    private String appSecret;

    @Schema(description = "授权 AccessToken", example = "token")
    private String accessToken;

    @Schema(description = "刷新 Token", example = "refresh_token")
    private String refreshToken;

    @Schema(description = "Token 过期时间")
    private LocalDateTime tokenExpireTime;

    @Schema(description = "爬虫模式登录 Cookie")
    private String cookie;

    @Schema(description = "扩展配置 JSON（请求头、适配器标识、字段映射、CSS 选择器等）")
    private String extraConfig;

    @Schema(description = "状态：0=开启，1=关闭", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    @InEnum(value = CommonStatusEnum.class, message = "状态必须是 {value}")
    private Integer status;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

}
