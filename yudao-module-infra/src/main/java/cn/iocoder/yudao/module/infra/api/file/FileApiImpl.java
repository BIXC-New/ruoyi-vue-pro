package cn.iocoder.yudao.module.infra.api.file;

import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.infra.api.file.dto.FileResourceRespDTO;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_RESOURCE_LIMIT;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 文件 API 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class FileApiImpl implements FileApi {

    @Resource
    private FileService fileService;

    @Override
    public List<FileResourceRespDTO> getImageResources(Collection<String> urls) throws Exception {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        if (urls.size() > 2000) {
            throw exception(FILE_RESOURCE_LIMIT);
        }
        var resources = new LinkedHashMap<String, FileResourceRespDTO>();
        long total = 0;
        for (var file : fileService.getFileListByUrls(urls)) {
            if (resources.containsKey(file.getUrl()) || file.getType() == null || !file.getType().startsWith("image/")) {
                continue;
            }
            if (resources.size() >= 100 || file.getSize() == null || file.getSize() > 5 * 1024 * 1024 || total + file.getSize() > 64 * 1024 * 1024) {
                throw exception(FILE_RESOURCE_LIMIT);
            }
            byte[] bytes = fileService.getFileContent(file.getConfigId(), file.getPath());
            if (bytes == null || bytes.length > 5 * 1024 * 1024 || total + bytes.length > 64 * 1024 * 1024) {
                throw exception(FILE_RESOURCE_LIMIT);
            }
            total += bytes.length;
            var resource = new FileResourceRespDTO();
            resource.setUrl(file.getUrl());
            resource.setName(file.getName());
            resource.setType(file.getType());
            resource.setContent(bytes);
            resources.put(file.getUrl(), resource);
        }
        return new ArrayList<>(resources.values());
    }

    @Override
    public String createFile(byte[] content, String name, String directory, String type) {
        return fileService.createFile(content, name, directory, type);
    }

    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        return fileService.presignGetUrl(url, expirationSeconds);
    }

    @Override
    public List<String> presignGetUrlList(List<String> urls, Integer expirationSeconds) {
        return fileService.presignGetUrlList(urls, expirationSeconds);
    }

}
