package cn.iocoder.yudao.module.product.service.source;

import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuImportReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuImportRespVO;
import jakarta.validation.Valid;

/**
 * 第三方商品采集导入 Service 接口
 */
public interface ProductSourceImportService {

    /**
     * 从第三方平台采集商品并生成前端回填预览数据（不直接入库）
     *
     * @param reqVO 采集请求
     * @return 标准化商品预览数据
     */
    ProductSpuImportRespVO importPreview(@Valid ProductSpuImportReqVO reqVO);

}
