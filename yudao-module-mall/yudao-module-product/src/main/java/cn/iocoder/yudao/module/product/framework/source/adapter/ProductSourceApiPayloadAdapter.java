package cn.iocoder.yudao.module.product.framework.source.adapter;

import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import cn.iocoder.yudao.module.product.framework.source.model.ProductSourceNormalizedProduct;

/**
 * 第三方 API 报文适配器接口
 * <p>
 * 用于解耦「HTTP 调用」与「不同第三方平台差异化的 API 响应报文解析」。
 * 后续对接 1688 官方开放平台、聚合数据服务或自建采集网关时，只需新增实现类即可。
 */
public interface ProductSourceApiPayloadAdapter {

    /**
     * 适配器唯一标识（可在平台配置 extraConfig.adapterCode 中显式指定）
     */
    String getCode();

    /**
     * 是否支持该平台或显式指定的适配器编码
     *
     * @param platformCode 平台标识
     * @param adapterCode  配置中显式指定的适配器编码，可为空
     * @return 是否匹配
     */
    boolean supports(String platformCode, String adapterCode);

    /**
     * 匹配优先级，数值越小优先级越高
     */
    default int getOrder() {
        return 100;
    }

    /**
     * 将第三方 API 原始响应报文适配为系统标准商品模型
     *
     * @param config        平台配置
     * @param sourceUrlOrId 来源 URL 或 ID
     * @param rawPayload    第三方 API 返回的原始 JSON/文本报文
     * @return 标准化商品模型
     */
    ProductSourceNormalizedProduct adapt(ProductSourcePlatformDO config, String sourceUrlOrId, String rawPayload);

}
