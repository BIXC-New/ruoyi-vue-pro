package cn.iocoder.yudao.module.infra.api.file.dto;
import lombok.Data;
/** 系统已归档图片，不通过用户提供的 URL 发起网络下载。 */
@Data
public class FileResourceRespDTO {
    private String url;
    private String name;
    private String type;
    private byte[] content;
}
