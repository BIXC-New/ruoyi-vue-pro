package cn.iocoder.yudao.module.product.service.source;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.product.controller.admin.property.vo.property.ProductPropertySaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.property.vo.value.ProductPropertyValueSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuImportReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuImportRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.source.ProductSourcePlatformDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.enums.source.ProductSourceTypeEnum;
import cn.iocoder.yudao.module.product.framework.source.ProductSourceFetcher;
import cn.iocoder.yudao.module.product.framework.source.ProductSourceFetcherFactory;
import cn.iocoder.yudao.module.product.framework.source.model.ProductSourceNormalizedProduct;
import cn.iocoder.yudao.module.product.framework.source.util.SourceUrlValidator;
import cn.iocoder.yudao.module.product.service.property.ProductPropertyService;
import cn.iocoder.yudao.module.product.service.property.ProductPropertyValueService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 第三方商品采集导入 Service 实现类
 */
@Slf4j
@Service
@Validated
public class ProductSourceImportServiceImpl implements ProductSourceImportService {

    private static final int MAX_TRANSFER_IMAGE_BYTES = 5 * 1024 * 1024;

    @Resource
    private ProductSourcePlatformService platformService;
    @Resource
    private ProductSourceFetcherFactory fetcherFactory;
    @Resource
    private ProductPropertyService productPropertyService;
    @Resource
    private ProductPropertyValueService productPropertyValueService;
    @Resource
    private ProductSpuMapper productSpuMapper;
    @Resource
    private ObjectProvider<FileApi> fileApiProvider;

    private final HttpClient imageHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public ProductSpuImportRespVO importPreview(ProductSpuImportReqVO reqVO) {
        ProductSourcePlatformDO platform = platformService.validatePlatformEnabled(reqVO.getPlatformId());
        ProductSourceFetcher fetcher = fetcherFactory.getFetcher(platform);
        ProductSourceNormalizedProduct normalized = fetcher.fetch(platform, reqVO.getSourceUrl());

        boolean downloadImages = Boolean.TRUE.equals(reqVO.getDownloadImages());
        Map<String, String> transferredCache = new HashMap<>();

        String picUrl = maybeTransferImage(normalized.getPicUrl(), downloadImages, transferredCache);
        List<String> sliderPics = new ArrayList<>();
        if (CollUtil.isNotEmpty(normalized.getSliderPicUrls())) {
            for (String slider : normalized.getSliderPicUrls()) {
                String resolved = maybeTransferImage(slider, downloadImages, transferredCache);
                if (StrUtil.isNotBlank(resolved) && !sliderPics.contains(resolved)) {
                    sliderPics.add(resolved);
                }
            }
        }
        if (StrUtil.isBlank(picUrl) && CollUtil.isNotEmpty(sliderPics)) {
            picUrl = sliderPics.get(0);
        }

        List<ProductSkuSaveReqVO> skus = convertSkus(normalized, picUrl, downloadImages, transferredCache);
        boolean specType = Boolean.TRUE.equals(normalized.getSpecType())
                && skus.stream().anyMatch(sku -> CollUtil.isNotEmpty(sku.getProperties()));

        ProductSpuImportRespVO respVO = new ProductSpuImportRespVO();
        respVO.setSourceType(ProductSourceTypeEnum.THIRD_PARTY.getType());
        respVO.setSourcePlatformId(platform.getId());
        respVO.setSourcePlatformCode(platform.getPlatformCode());
        respVO.setSourceProductId(normalized.getSourceProductId());
        respVO.setSourceUrl(normalized.getSourceUrl());
        respVO.setName(normalized.getName());
        respVO.setKeyword(normalized.getKeyword());
        respVO.setIntroduction(normalized.getIntroduction());
        respVO.setDescription(normalized.getDescription());
        respVO.setPicUrl(picUrl);
        respVO.setSliderPicUrls(sliderPics);
        respVO.setSpecType(specType);
        respVO.setSkus(skus);

        // 查询当前租户下是否已引入过同一 sourceProductId，供前端友好提示
        if (StrUtil.isNotBlank(normalized.getSourceProductId())) {
            ProductSpuDO existing = productSpuMapper.selectFirstBySourceProductId(normalized.getSourceProductId());
            if (existing != null) {
                respVO.setDuplicateSpuId(existing.getId());
                respVO.setDuplicateSpuName(existing.getName());
            }
        }
        return respVO;
    }

    private List<ProductSkuSaveReqVO> convertSkus(ProductSourceNormalizedProduct normalized, String defaultPicUrl,
                                                  boolean downloadImages, Map<String, String> transferredCache) {
        List<ProductSkuSaveReqVO> result = new ArrayList<>();
        Map<String, Long> propertyIdCache = new HashMap<>();
        Map<String, Long> propertyValueIdCache = new HashMap<>();

        List<ProductSourceNormalizedProduct.SkuItem> sourceSkus = normalized.getSkus();
        if (CollUtil.isEmpty(sourceSkus)) {
            ProductSkuSaveReqVO single = new ProductSkuSaveReqVO();
            single.setName(normalized.getName());
            single.setPrice(0);
            single.setMarketPrice(0);
            single.setCostPrice(0);
            single.setStock(100);
            single.setPicUrl(StrUtil.emptyToDefault(defaultPicUrl, ""));
            single.setBarCode("");
            result.add(single);
            return result;
        }

        for (ProductSourceNormalizedProduct.SkuItem item : sourceSkus) {
            ProductSkuSaveReqVO sku = new ProductSkuSaveReqVO();
            sku.setSourceSkuId(item.getSourceSkuId());
            sku.setName(StrUtil.firstNonBlank(item.getName(), normalized.getName()));
            sku.setCostPrice(Objects.requireNonNullElse(item.getCostPrice(), 0));
            sku.setPrice(Objects.requireNonNullElse(item.getPrice(), sku.getCostPrice()));
            sku.setMarketPrice(Objects.requireNonNullElse(item.getMarketPrice(), sku.getPrice()));
            sku.setStock(Objects.requireNonNullElse(item.getStock(), 100));
            sku.setBarCode(StrUtil.emptyToDefault(item.getBarCode(), ""));
            String skuPic = maybeTransferImage(StrUtil.firstNonBlank(item.getPicUrl(), defaultPicUrl),
                    downloadImages, transferredCache);
            sku.setPicUrl(StrUtil.emptyToDefault(skuPic, ""));

            if (CollUtil.isNotEmpty(item.getProperties())) {
                List<ProductSkuSaveReqVO.Property> props = new ArrayList<>();
                for (ProductSourceNormalizedProduct.PropertyItem propItem : item.getProperties()) {
                    if (StrUtil.isBlank(propItem.getPropertyName()) || StrUtil.isBlank(propItem.getValueName())) {
                        continue;
                    }
                    Long propertyId = propertyIdCache.computeIfAbsent(propItem.getPropertyName(), name -> {
                        ProductPropertySaveReqVO propReqVO = new ProductPropertySaveReqVO();
                        propReqVO.setName(name);
                        return productPropertyService.createProperty(propReqVO);
                    });
                    String valKey = propertyId + "::" + propItem.getValueName();
                    Long valueId = propertyValueIdCache.computeIfAbsent(valKey, k -> {
                        ProductPropertyValueSaveReqVO valReqVO = new ProductPropertyValueSaveReqVO();
                        valReqVO.setPropertyId(propertyId);
                        valReqVO.setName(propItem.getValueName());
                        return productPropertyValueService.createPropertyValue(valReqVO);
                    });
                    props.add(new ProductSkuSaveReqVO.Property(propertyId, propItem.getPropertyName(),
                            valueId, propItem.getValueName()));
                }
                sku.setProperties(props);
            }
            result.add(sku);
        }
        return result;
    }

    private String maybeTransferImage(String externalUrl, boolean downloadImages, Map<String, String> cache) {
        if (StrUtil.isBlank(externalUrl)) {
            return externalUrl;
        }
        if (!downloadImages) {
            return externalUrl;
        }
        FileApi fileApi = fileApiProvider.getIfAvailable();
        if (fileApi == null) {
            return externalUrl;
        }
        return cache.computeIfAbsent(externalUrl, url -> transferSingleImage(fileApi, url));
    }

    private String transferSingleImage(FileApi fileApi, String externalUrl) {
        try {
            URI uri = SourceUrlValidator.validateExternalHttpUrl(externalUrl);
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = imageHttpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300 && resp.body() != null
                    && resp.body().length > 0 && resp.body().length <= MAX_TRANSFER_IMAGE_BYTES) {
                String contentType = resp.headers().firstValue("Content-Type").orElse("image/jpeg");
                String ext = contentType.contains("png") ? ".png" : contentType.contains("webp") ? ".webp" : ".jpg";
                return fileApi.createFile(resp.body(), "source-" + System.currentTimeMillis() + ext,
                        "product-source", contentType);
            }
        } catch (Exception ex) {
            log.warn("[transferSingleImage][可选图片转存失败，保留原地址 url={}]", externalUrl, ex);
        }
        return externalUrl;
    }

}
