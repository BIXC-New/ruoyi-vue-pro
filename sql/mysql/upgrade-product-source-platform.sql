-- ============================================================================
-- 第三方平台商品引入（多租户隔离）数据库升级脚本
-- ============================================================================

-- 1. 新增第三方商品平台配置表
CREATE TABLE IF NOT EXISTS `product_source_platform` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name` varchar(64) NOT NULL COMMENT '配置名称',
    `platform_code` varchar(32) NOT NULL COMMENT '平台标识（如 1688、taobao、generic）',
    `fetch_mode` tinyint NOT NULL DEFAULT 1 COMMENT '采集方式：1=API接口，2=网页爬虫',
    `base_url` varchar(512) NOT NULL COMMENT '平台接口网关或站点 URL',
    `app_key` varchar(128) DEFAULT NULL COMMENT 'API AppKey',
    `app_secret` varchar(256) DEFAULT NULL COMMENT 'API AppSecret',
    `access_token` varchar(512) DEFAULT NULL COMMENT '授权 AccessToken',
    `refresh_token` varchar(512) DEFAULT NULL COMMENT '刷新 Token',
    `token_expire_time` datetime DEFAULT NULL COMMENT 'Token 过期时间',
    `cookie` text DEFAULT NULL COMMENT '爬虫模式登录 Cookie',
    `extra_config` text DEFAULT NULL COMMENT '扩展配置 JSON（请求头、适配器/解析规则等）',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0=开启，1=关闭',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_platform` (`tenant_id`, `platform_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品第三方来源平台配置';

-- 2. 扩展商品 SPU 表，增加来源与溯源 URL 字段
ALTER TABLE `product_spu`
    ADD COLUMN `source_type` tinyint NOT NULL DEFAULT 0 COMMENT '商品来源类型：0=自有商品，1=第三方平台' AFTER `status`,
    ADD COLUMN `source_platform_id` bigint DEFAULT NULL COMMENT '第三方平台配置编号' AFTER `source_type`,
    ADD COLUMN `source_platform_code` varchar(32) DEFAULT NULL COMMENT '第三方平台标识' AFTER `source_platform_id`,
    ADD COLUMN `source_product_id` varchar(128) DEFAULT NULL COMMENT '第三方平台商品编号' AFTER `source_platform_code`,
    ADD COLUMN `source_url` varchar(1024) DEFAULT NULL COMMENT '商品来源 URL' AFTER `source_product_id`,
    ADD INDEX `idx_tenant_source_product` (`tenant_id`, `source_product_id`);

-- 3. 扩展商品 SKU 表，预留第三方 SKU 映射编号
ALTER TABLE `product_sku`
    ADD COLUMN `source_sku_id` varchar(128) DEFAULT NULL COMMENT '第三方平台 SKU 编号' AFTER `bar_code`;

-- 4. 菜单权限（挂载在 商品中心 parent_id = 209 下）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
    (25400, '货源平台配置', '', 2, 10, 209, 'source-platform', 'ep:connection', 'mall/product/sourcePlatform/index', 'ProductSourcePlatform', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (25401, '平台配置查询', 'product:source-platform:query', 3, 1, 25400, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (25402, '平台配置创建', 'product:source-platform:create', 3, 2, 25400, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (25403, '平台配置更新', 'product:source-platform:update', 3, 3, 25400, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (25404, '平台配置删除', 'product:source-platform:delete', 3, 4, 25400, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
    (25405, '商品采集导入', 'product:spu:import', 3, 6, 220, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 5. 数据字典类型与字典项
INSERT INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
VALUES
    (630, '商品来源类型', 'product_source_type', 0, '商品 SPU 来源类型', '1', NOW(), '1', NOW(), b'0', NULL),
    (631, '第三方货源平台', 'product_source_platform', 0, '第三方货源平台标识', '1', NOW(), '1', NOW(), b'0', NULL),
    (632, '货源采集方式', 'product_source_fetch_mode', 0, '第三方商品采集方式', '1', NOW(), '1', NOW(), b'0', NULL);

INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
    (3030, 1, '自有商品', '0', 'product_source_type', 0, 'primary', '', '自有商品', '1', NOW(), '1', NOW(), b'0'),
    (3031, 2, '第三方平台', '1', 'product_source_type', 0, 'warning', '', '第三方平台商品', '1', NOW(), '1', NOW(), b'0'),
    (3032, 1, '1688 批发', '1688', 'product_source_platform', 0, 'warning', '', '阿里巴巴 1688', '1', NOW(), '1', NOW(), b'0'),
    (3033, 2, '通用网页/其他', 'generic', 'product_source_platform', 0, 'info', '', '通用网页或自定义适配器', '1', NOW(), '1', NOW(), b'0'),
    (3034, 1, 'API 接口', '1', 'product_source_fetch_mode', 0, 'success', '', '通过开放平台或第三方 API 获取', '1', NOW(), '1', NOW(), b'0'),
    (3035, 2, '网页爬虫', '2', 'product_source_fetch_mode', 0, 'primary', '', '通过页面抓取解析获取', '1', NOW(), '1', NOW(), b'0');
