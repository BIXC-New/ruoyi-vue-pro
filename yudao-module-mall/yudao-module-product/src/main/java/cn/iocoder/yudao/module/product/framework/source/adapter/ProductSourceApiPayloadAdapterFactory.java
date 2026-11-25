package cn.iocoder.yudao.module.product.framework.source.adapter;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import cn.iocoder.yudao.module.product.framework.source.util.SourcePayloadJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOURCE_API_PAYLOAD_INVALID;

/**
 * 第三方 API 报文适配器工厂
 */
@Component
@RequiredArgsConstructor
public class ProductSourceApiPayloadAdapterFactory {

    private final List<ProductSourceApiPayloadAdapter> adapters;

    public ProductSourceApiPayloadAdapter getAdapter(ProductSourcePlatformDO config) {
        JsonNode extra = SourcePayloadJsonUtils.parseTreeQuietly(config.getExtraConfig());
        String adapterCode = SourcePayloadJsonUtils.firstText(extra, "adapterCode");
        return adapters.stream()
                .filter(adapter -> adapter.supports(config.getPlatformCode(), adapterCode))
                .min(Comparator.comparingInt(ProductSourceApiPayloadAdapter::getOrder))
                .orElseThrow(() -> exception(SOURCE_API_PAYLOAD_INVALID,
                        StrUtil.format("未找到平台 {} 的报文适配器", config.getPlatformCode())));
    }

}
