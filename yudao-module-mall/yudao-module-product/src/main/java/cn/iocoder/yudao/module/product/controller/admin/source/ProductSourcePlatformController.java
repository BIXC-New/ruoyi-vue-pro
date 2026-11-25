package cn.iocoder.yudao.module.product.controller.admin.source;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.source.vo.ProductSourcePlatformPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.source.vo.ProductSourcePlatformRespVO;
import cn.iocoder.yudao.module.product.controller.admin.source.vo.ProductSourcePlatformSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.source.vo.ProductSourcePlatformSimpleRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import cn.iocoder.yudao.module.product.service.source.ProductSourcePlatformService;
import cn.iocoder.yudao.module.product.service.source.ProductSourcePlatformServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 第三方货源平台配置")
@RestController
@RequestMapping("/product/source-platform")
@Validated
public class ProductSourcePlatformController {

    @Resource
    private ProductSourcePlatformService platformService;

    @PostMapping("/create")
    @Operation(summary = "创建第三方货源平台配置")
    @PreAuthorize("@ss.hasPermission('product:source-platform:create')")
    public CommonResult<Long> createPlatform(@Valid @RequestBody ProductSourcePlatformSaveReqVO createReqVO) {
        return success(platformService.createPlatform(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新第三方货源平台配置")
    @PreAuthorize("@ss.hasPermission('product:source-platform:update')")
    public CommonResult<Boolean> updatePlatform(@Valid @RequestBody ProductSourcePlatformSaveReqVO updateReqVO) {
        platformService.updatePlatform(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除第三方货源平台配置")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('product:source-platform:delete')")
    public CommonResult<Boolean> deletePlatform(@RequestParam("id") Long id) {
        platformService.deletePlatform(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得第三方货源平台配置")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('product:source-platform:query')")
    public CommonResult<ProductSourcePlatformRespVO> getPlatform(@RequestParam("id") Long id) {
        ProductSourcePlatformDO platform = platformService.getPlatform(id);
        return success(toMaskedRespVO(platform));
    }

    @GetMapping("/page")
    @Operation(summary = "获得第三方货源平台配置分页")
    @PreAuthorize("@ss.hasPermission('product:source-platform:query')")
    public CommonResult<PageResult<ProductSourcePlatformRespVO>> getPlatformPage(@Valid ProductSourcePlatformPageReqVO pageVO) {
        PageResult<ProductSourcePlatformDO> pageResult = platformService.getPlatformPage(pageVO);
        PageResult<ProductSourcePlatformRespVO> respPage = BeanUtils.toBean(pageResult, ProductSourcePlatformRespVO.class);
        if (respPage.getList() != null) {
            respPage.getList().forEach(ProductSourcePlatformController::maskSecrets);
        }
        return success(respPage);
    }

    @GetMapping("/list-all-simple")
    @Operation(summary = "获得已启用的第三方货源平台精简列表")
    @PreAuthorize("@ss.hasPermission('product:spu:create') or @ss.hasPermission('product:spu:update') or @ss.hasPermission('product:source-platform:query')")
    public CommonResult<List<ProductSourcePlatformSimpleRespVO>> getSimplePlatformList() {
        List<ProductSourcePlatformDO> list = platformService.getPlatformListByStatus(CommonStatusEnum.ENABLE.getStatus());
        return success(BeanUtils.toBean(list, ProductSourcePlatformSimpleRespVO.class));
    }

    private static ProductSourcePlatformRespVO toMaskedRespVO(ProductSourcePlatformDO platform) {
        if (platform == null) {
            return null;
        }
        ProductSourcePlatformRespVO respVO = BeanUtils.toBean(platform, ProductSourcePlatformRespVO.class);
        maskSecrets(respVO);
        return respVO;
    }

    private static void maskSecrets(ProductSourcePlatformRespVO vo) {
        vo.setAppSecret(maskValue(vo.getAppSecret()));
        vo.setAccessToken(maskValue(vo.getAccessToken()));
        vo.setRefreshToken(maskValue(vo.getRefreshToken()));
        vo.setCookie(maskValue(vo.getCookie()));
    }

    private static String maskValue(String raw) {
        if (StrUtil.isBlank(raw)) {
            return raw;
        }
        if (raw.length() <= 8) {
            return ProductSourcePlatformServiceImpl.SECRET_MASK;
        }
        return StrUtil.subPre(raw, 4) + ProductSourcePlatformServiceImpl.SECRET_MASK + StrUtil.subSuf(raw, raw.length() - 4);
    }

}
