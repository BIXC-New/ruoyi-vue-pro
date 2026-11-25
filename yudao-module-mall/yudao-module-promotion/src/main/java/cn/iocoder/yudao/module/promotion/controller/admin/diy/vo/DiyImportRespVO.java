package cn.iocoder.yudao.module.promotion.controller.admin.diy.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "管理后台 - 装修包导入结果")
public class DiyImportRespVO {
    @Schema(description = "新建的模板或页面编号")
    private Long id;
    @Schema(description = "已归档到系统文件存储的素材")
    private List<Asset> assets;

    @Data
    public static class Asset {
        private String path;
        private String url;
    }
}
