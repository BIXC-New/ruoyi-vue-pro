package cn.iocoder.yudao.module.product.dal.mysql.source;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.source.vo.ProductSourcePlatformPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 商品第三方来源平台配置 Mapper
 */
@Mapper
public interface ProductSourcePlatformMapper extends BaseMapperX<ProductSourcePlatformDO> {

    default PageResult<ProductSourcePlatformDO> selectPage(ProductSourcePlatformPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductSourcePlatformDO>()
                .likeIfPresent(ProductSourcePlatformDO::getName, reqVO.getName())
                .eqIfPresent(ProductSourcePlatformDO::getPlatformCode, reqVO.getPlatformCode())
                .eqIfPresent(ProductSourcePlatformDO::getFetchMode, reqVO.getFetchMode())
                .eqIfPresent(ProductSourcePlatformDO::getStatus, reqVO.getStatus())
                .orderByAsc(ProductSourcePlatformDO::getSort)
                .orderByDesc(ProductSourcePlatformDO::getId));
    }

    default List<ProductSourcePlatformDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<ProductSourcePlatformDO>()
                .eqIfPresent(ProductSourcePlatformDO::getStatus, status)
                .orderByAsc(ProductSourcePlatformDO::getSort)
                .orderByDesc(ProductSourcePlatformDO::getId));
    }

}
