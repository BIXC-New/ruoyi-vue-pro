package cn.iocoder.yudao.module.product.controller.admin.source.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 第三方货源平台配置分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProductSourcePlatformPageReqVO extends PageParam {

    @Schema(description = "配置名称", example = "1688")
    private String name;

    @Schema(description = "平台标识", example = "1688")
    private String platformCode;

    @Schema(description = "采集方式：1=API接口，2=网页爬虫", example = "1")
    private Integer fetchMode;

    @Schema(description = "状态：0=开启，1=关闭", example = "0")
    private Integer status;

}
