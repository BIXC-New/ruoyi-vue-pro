package cn.iocoder.yudao.module.product.framework.source;

import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import cn.iocoder.yudao.module.product.framework.source.model.ProductSourceNormalizedProduct;

/**
 * 第三方商品采集策略接口（按平台标识与采集方式路由）
 */
public interface ProductSourceFetcher {

    /**
     * 是否支持指定平台与采集方式
     *
     * @param platformCode 平台标识，如 1688、generic
     * @param fetchMode    采集方式：1=API，2=爬虫
     * @return 是否支持
     */
    boolean supports(String platformCode, Integer fetchMode);

    /**
     * 匹配优先级，数值越小优先级越高
     */
    default int getOrder() {
        return 100;
    }

    /**
     * 执行采集并返回标准化商品信息
     *
     * @param config        第三方平台配置
     * @param sourceUrlOrId 商品 URL 或第三方商品 ID
     * @return 标准化商品模型
     */
    ProductSourceNormalizedProduct fetch(ProductSourcePlatformDO config, String sourceUrlOrId);

}
