# 装修 ZIP 导入

## 使用

管理端“商城装修 → 装修模板”及“装修页面”列表均新增 **导入 ZIP**。填写新记录名称并选择 ZIP，即可导入；导入后仍在原装修编辑器里继续修改、保存与预览。不会覆盖或自动启用现有模板。

入口复用原创建权限，不增加颜色专用菜单或按钮：

- POST /admin-api/promotion/diy-template/import：promotion:diy-template:create
- POST /admin-api/promotion/diy-page/import：promotion:diy-page:create

均为 multipart/form-data，字段为 name（必填，最多 64 字）、file（ZIP）。返回原 CommonResult，data 中包含新记录 id 和素材 assets（原包路径 path、归档后的 url）。

## 下载模板

装修模板列表的每一行提供通用的 **下载** 操作，保存为“模板名称.zip”。接口为 `GET /admin-api/promotion/diy-template/export?id=模板编号`，沿用 `promotion:diy-template:query` 权限，响应为 `application/zip` 附件。

包内 `diy.json` 包含基础设置（包括底部导航和主题参数）、首页、个人中心、备注和预览图。配置中引用且在系统文件管理有归档记录的图片，通过原文件存储读取，写入 `assets/`，配置引用转换为 `asset://`。不修改源模板、不新增文件记录、不切换启用状态。

外部 URL 保留原值，并在 `externalAssets` 列出；后端不会访问这些 URL。项目内置静态资源路径保持原值，需要目标商城具备相同静态资源；商品、分类等业务 ID 也保留，下载包不包含商品数据库。迁移到其他环境时需核对这些依赖。超过导入限制或归档图片读取失败时明确提示，不生成缺少归档素材的包。

下载包可直接从“导入 ZIP”重新导入。名称使用导入表单填写的名称，创建独立的未启用模板；图片重新归档到当前系统文件存储。没有归档图片、只有装修配置的包也支持导入。

## 包结构

### 只有图片

```text
images.zip
  assets/
    01-banner.png
    02-recommend.webp
```

不需要配置文件。图片按文件名排序，生成可编辑的 ImageBar 图片展示组件。模板入口会新建首页，并继承当前启用模板的基础设置和个人中心；没有启用模板时使用基础导航默认值。页面入口生成独立装修页面。纯图片包不会自动推断商品、链接或业务布局，需要在装修编辑器中配置。

### 带装修配置

```text
theme.zip
  diy.json
  assets/
    banner.png
    nav-home.svg
```

配置文件必须位于根目录，UTF-8 编码。模板例子：

```json
{
  "version": 1,
  "kind": "template",
  "property": {
    "tabBar": {
      "theme": "blackGold",
      "themeConfig": { "mode": "dark", "followTheme": true },
      "style": { "bgType": "color", "bgColor": "#24211e", "color": "#bcb2a4", "activeColor": "#d6b982" },
      "items": [
        { "text": "首页", "url": "/pages/index/index", "iconUrl": "asset://assets/nav-home.svg", "activeIconUrl": "asset://assets/nav-home.svg" }
      ]
    }
  },
  "home": {
    "page": { "backgroundColor": "#171614" },
    "navigationBar": { "styleType": "normal", "showType": "always", "bgType": "color", "bgColor": "#171614", "mpCells": [], "otherCells": [] },
    "components": [
      { "id": "ImageBar", "property": { "imgUrl": "asset://assets/banner.png", "url": "", "style": { "bgType": "color", "bgColor": "#171614", "marginBottom": 8 } } }
    ]
  }
}
```

- property：模板基础配置，需包含 tabBar；保留现有装修组件的字段格式。
- home / user：完整页面配置。home 未提供时生成图片组件；user 未提供时继承当前启用模板个人中心。
- 页面包使用 kind: "page"，将完整页面配置放在 property 中。
- asset://assets/banner.png 在配置内任何层级、以及富文本中的同类引用均可替换。独立字符串 assets/banner.png、./assets/banner.png 也支持。
- ZIP 中引用不存在的图片会在上传前拒绝；HTTP 图片地址保留原样，不下载远程资源。
- 顶层 name/id/used 等不用于覆盖记录：名称采用导入表单，新记录独立生成，启用仍由原“使用”流程操作。

可直接检查或试导入 [完整模板样例](example-template.zip)、[纯图片样例](example-images.zip)。模板样例复用了本项目本地测试商品关联，不应作为正式商品目录使用。

## 文件管理与一致性

图片通过现有 FileApi.createFile 归档，由当前主存储配置选择本地、数据库或对象存储等后端；不会另外写入静态目录。所有成功上传均生成原 infra_file 文件管理记录，保留配置编号、路径、名称、类型与大小。

每个包使用 diy/import/<UUID> 分组，每张图片再使用独立子目录，避免不同 ZIP 子目录里的同名图片在关闭文件名随机后缀时相互覆盖。最终文件路径仍由原 FileService 的日期、文件名与存储规则生成。

包校验在文件上传前完成。文件 IO 在装修数据库事务之外；模板及其首页/个人中心的创建在同一个事务中。后续创建失败时，不会留下半套装修记录；已成功归档的素材仍能在文件管理中按导入目录查看和清理，行为与普通上传后取消编辑一致。

ZIP 本身只用于解析，不作为额外资源保存。没有向商城代码目录解压文件。

## 限制

- ZIP ≤ 10MB；最多 200 个条目、100 张图片；解压总量 ≤ 64MB。
- 单张图片 ≤ 5MB，diy.json ≤ 1MB。
- 支持 PNG、JPEG、GIF、WebP，以及静态 SVG 子集。SVG 允许基础形状、文字和内部渐变/引用，拒绝脚本、事件、外部资源、style、动画和 DOCTYPE。
- 拒绝目录穿越、绝对路径、盘符、重复路径及伪装的图片。兼容 Windows ZIP 的反斜杠分隔，先统一路径再校验。
- 不支持加密 ZIP、RAR 或 7z。

## 验证记录

- Java 17 编译、DiyImportServiceTest 针对性测试；前端装修模块 vue-tsc 与变更文件 Prettier 检查。
- 从实际管理端上传完整配置包，创建未启用模板 16，归档 8 张图片；重新查询验证主题参数和图片地址已持久化。
- 从实际页面列表上传纯图片包，创建页面 34，归档 2 张图片；编辑图片跳转链接并保存，重新打开后链接及图片仍存在。
- 文件管理查询到对应 10 条记录，使用当前配置 29；图片 URL HTTP 200。商城 H5 成功加载导入模板和页面中的归档图片。
- 未登录导入请求返回 401。当前启用模板仍为原 5 号，13/15 等已有模板保留，未自动切换。
- 同名目录隔离另有针对性测试与本地导入验证；最终结果见本次交付说明。

本次验证为本地后台与 H5，不包含线上发布、微信真机或支付流程。
