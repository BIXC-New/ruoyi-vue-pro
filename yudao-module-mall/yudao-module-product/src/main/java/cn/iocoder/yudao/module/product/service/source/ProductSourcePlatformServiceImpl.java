package cn.iocoder.yudao.module.product.service.source;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.source.vo.ProductSourcePlatformPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.source.vo.ProductSourcePlatformSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import cn.iocoder.yudao.module.product.dal.mysql.source.ProductSourcePlatformMapper;
import cn.iocoder.yudao.module.product.enums.source.ProductSourceFetchModeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

/**
 * 商品第三方来源平台配置 Service 实现类
 */
@Service
@Validated
public class ProductSourcePlatformServiceImpl implements ProductSourcePlatformService {

    /**
     * 前端脱敏占位标识：更新时若传回包含该掩码的值，则保留数据库中原敏感配置
     */
    public static final String SECRET_MASK = "******";

    @Resource
    private ProductSourcePlatformMapper platformMapper;

    @Override
    public Long createPlatform(ProductSourcePlatformSaveReqVO createReqVO) {
        validateModeAuth(createReqVO.getFetchMode(), createReqVO.getBaseUrl(),
                createReqVO.getAppKey(), createReqVO.getAccessToken());
        ProductSourcePlatformDO platform = BeanUtils.toBean(createReqVO, ProductSourcePlatformDO.class);
        if (platform.getSort() == null) {
            platform.setSort(0);
        }
        platformMapper.insert(platform);
        return platform.getId();
    }

    @Override
    public void updatePlatform(ProductSourcePlatformSaveReqVO updateReqVO) {
        ProductSourcePlatformDO dbPlatform = validatePlatformExists(updateReqVO.getId());
        String resolvedAppSecret = resolveSecretOnUpdate(updateReqVO.getAppSecret(), dbPlatform.getAppSecret());
        String resolvedAccessToken = resolveSecretOnUpdate(updateReqVO.getAccessToken(), dbPlatform.getAccessToken());
        String resolvedRefreshToken = resolveSecretOnUpdate(updateReqVO.getRefreshToken(), dbPlatform.getRefreshToken());
        String resolvedCookie = resolveSecretOnUpdate(updateReqVO.getCookie(), dbPlatform.getCookie());

        validateModeAuth(updateReqVO.getFetchMode(), updateReqVO.getBaseUrl(),
                updateReqVO.getAppKey(), resolvedAccessToken);

        ProductSourcePlatformDO updateObj = BeanUtils.toBean(updateReqVO, ProductSourcePlatformDO.class);
        updateObj.setAppSecret(resolvedAppSecret);
        updateObj.setAccessToken(resolvedAccessToken);
        updateObj.setRefreshToken(resolvedRefreshToken);
        updateObj.setCookie(resolvedCookie);
        platformMapper.updateById(updateObj);
    }

    @Override
    public void deletePlatform(Long id) {
        validatePlatformExists(id);
        platformMapper.deleteById(id);
    }

    @Override
    public ProductSourcePlatformDO getPlatform(Long id) {
        return platformMapper.selectById(id);
    }

    @Override
    public PageResult<ProductSourcePlatformDO> getPlatformPage(ProductSourcePlatformPageReqVO pageReqVO) {
        return platformMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ProductSourcePlatformDO> getPlatformListByStatus(Integer status) {
        return platformMapper.selectListByStatus(status);
    }

    @Override
    public ProductSourcePlatformDO validatePlatformEnabled(Long id) {
        ProductSourcePlatformDO platform = validatePlatformExists(id);
        if (ObjectUtil.notEqual(platform.getStatus(), CommonStatusEnum.ENABLE.getStatus())) {
            throw exception(SOURCE_PLATFORM_DISABLED, platform.getName());
        }
        return platform;
    }

    @Override
    public ProductSourcePlatformDO validatePlatformExists(Long id) {
        ProductSourcePlatformDO platform = platformMapper.selectById(id);
        if (platform == null) {
            throw exception(SOURCE_PLATFORM_NOT_EXISTS);
        }
        return platform;
    }

    private void validateModeAuth(Integer fetchMode, String baseUrl, String appKey, String accessToken) {
        if (Objects.equals(ProductSourceFetchModeEnum.API.getMode(), fetchMode)) {
            if (StrUtil.isBlank(baseUrl) || (StrUtil.isBlank(appKey) && StrUtil.isBlank(accessToken))) {
                throw exception(SOURCE_API_AUTH_INCOMPLETE);
            }
        }
    }

    private static String resolveSecretOnUpdate(String incoming, String dbValue) {
        if (StrUtil.isBlank(incoming) || StrUtil.contains(incoming, SECRET_MASK)) {
            return dbValue;
        }
        return incoming;
    }

}
