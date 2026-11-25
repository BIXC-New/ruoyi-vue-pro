package cn.iocoder.yudao.module.product.enums.source;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 第三方商品采集方式枚举
 */
@Getter
@AllArgsConstructor
public enum ProductSourceFetchModeEnum implements ArrayValuable<Integer> {

    API(1, "API 接口"),
    CRAWLER(2, "网页爬虫");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(ProductSourceFetchModeEnum::getMode).toArray(Integer[]::new);

    private final Integer mode;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
