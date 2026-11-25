package cn.iocoder.yudao.module.product.dal.dataobject.source;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.product.enums.source.ProductSourceFetchModeEnum;
import cn.iocoder.yudao.module.product.enums.source.ProductSourcePlatformEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 商品第三方来源平台配置 DO
 * <p>
 * 继承 {@link TenantBaseDO}，支持多租户隔离。
 */
@TableName(value = "product_source_platform", autoResultMap = true)
@KeySequence("product_source_platform_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSourcePlatformDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * 配置名称
     */
    private String name;

    /**
     * 平台标识
     * <p>
     * 枚举 {@link ProductSourcePlatformEnum}
     */
    private String platformCode;

    /**
     * 采集方式
     * <p>
     * 枚举 {@link ProductSourceFetchModeEnum}
     */
    private Integer fetchMode;

    /**
     * 平台接口网关或站点 URL
     */
    private String baseUrl;

    /**
     * API AppKey
     */
    private String appKey;

    /**
     * API AppSecret
     */
    private String appSecret;

    /**
     * 授权 AccessToken
     */
    private String accessToken;

    /**
     * 刷新 Token
     */
    private String refreshToken;

    /**
     * Token 过期时间
     */
    private LocalDateTime tokenExpireTime;

    /**
     * 爬虫模式登录 Cookie
     */
    private String cookie;

    /**
     * 扩展配置 JSON（请求头、适配器标识、字段映射、CSS 选择器等）
     */
    private String extraConfig;

    /**
     * 状态
     * <p>
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 备注
     */
    private String remark;

}
