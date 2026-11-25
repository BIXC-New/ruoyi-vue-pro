package cn.iocoder.yudao.module.product.enums.source;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 第三方商品来源平台标识枚举
 */
@Getter
@AllArgsConstructor
public enum ProductSourcePlatformEnum implements ArrayValuable<String> {

    ALIBABA_1688("1688", "1688 批发"),
    GENERIC("generic", "通用网页/自定义");

    public static final String[] ARRAYS = Arrays.stream(values()).map(ProductSourcePlatformEnum::getCode).toArray(String[]::new);

    private final String code;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
