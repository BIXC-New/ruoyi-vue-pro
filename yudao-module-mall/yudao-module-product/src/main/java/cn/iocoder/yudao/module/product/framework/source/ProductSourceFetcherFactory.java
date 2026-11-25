package cn.iocoder.yudao.module.product.framework.source;

import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOURCE_ADAPTER_NOT_FOUND;

/**
 * 第三方商品采集策略工厂
 */
@Component
@RequiredArgsConstructor
public class ProductSourceFetcherFactory {

    private final List<ProductSourceFetcher> fetchers;

    public ProductSourceFetcher getFetcher(ProductSourcePlatformDO config) {
        return fetchers.stream()
                .filter(fetcher -> fetcher.supports(config.getPlatformCode(), config.getFetchMode()))
                .min(Comparator.comparingInt(ProductSourceFetcher::getOrder))
                .orElseThrow(() -> exception(SOURCE_ADAPTER_NOT_FOUND,
                        config.getPlatformCode(), config.getFetchMode()));
    }

}
