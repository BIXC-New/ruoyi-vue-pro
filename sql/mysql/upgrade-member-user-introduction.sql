-- 为从旧版会员表升级的数据库补齐个人简介字段。
ALTER TABLE `member_user`
    ADD COLUMN `introduction` varchar(500) NULL DEFAULT NULL COMMENT '个人简介' AFTER `nickname`;
