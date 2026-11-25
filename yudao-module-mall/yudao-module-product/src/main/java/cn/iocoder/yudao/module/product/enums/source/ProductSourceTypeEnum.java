package cn.iocoder.yudao.module.product.enums.source;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 商品来源类型枚举
 */
@Getter
@AllArgsConstructor
public enum ProductSourceTypeEnum implements ArrayValuable<Integer> {

    OWN(0, "自有商品"),
    THIRD_PARTY(1, "第三方平台");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(ProductSourceTypeEnum::getType).toArray(Integer[]::new);

    private final Integer type;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
