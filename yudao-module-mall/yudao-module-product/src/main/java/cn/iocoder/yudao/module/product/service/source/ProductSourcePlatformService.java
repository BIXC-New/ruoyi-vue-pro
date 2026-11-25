package cn.iocoder.yudao.module.product.service.source;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.source.vo.ProductSourcePlatformPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.source.vo.ProductSourcePlatformSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 商品第三方来源平台配置 Service 接口
 */
public interface ProductSourcePlatformService {

    Long createPlatform(@Valid ProductSourcePlatformSaveReqVO createReqVO);

    void updatePlatform(@Valid ProductSourcePlatformSaveReqVO updateReqVO);

    void deletePlatform(Long id);

    ProductSourcePlatformDO getPlatform(Long id);

    PageResult<ProductSourcePlatformDO> getPlatformPage(ProductSourcePlatformPageReqVO pageReqVO);

    List<ProductSourcePlatformDO> getPlatformListByStatus(Integer status);

    /**
     * 校验平台配置是否存在且处于开启状态
     */
    ProductSourcePlatformDO validatePlatformEnabled(Long id);

    /**
     * 校验平台配置是否存在（不校验启用状态）
     */
    ProductSourcePlatformDO validatePlatformExists(Long id);

}
