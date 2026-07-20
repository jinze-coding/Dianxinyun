# 小程序五页优化预览

更新时间：2026-07-11。

本目录保存“概况、人员、质量、安全、我的”五页 V2 高保真设计稿。V2 已迁移到正式小程序五栏 tabBar，图片作为验收对照；`pages/design-preview/index` 仍保留为隔离预览。正式概况、人员、质量已接入新增真实接口，质量闭环已有对应数据表。

## V2 视觉与交互

- 使用柔和专业浅色体系，五页分别使用蓝灰、青绿、靛蓝、琥珀和雾蓝辅助色。
- 模块采用浅色标题区与白色内容区的连接式结构，列表取消独立硬边框和卡片嵌套。
- 底部五栏使用本地抗锯齿图标、半透明白色通栏和活动光晕。
- 支持页面切换、区域弹层、指标更新、分段筛选、列表错峰、视频切换、扫码线和按压反馈动效。
- 施工区域、人员搜索、质量筛选、摄像头切换及三种安全角色视图继续保留。

## 预览方式

在小程序工程目录执行：

```bash
npm run dev:h5
```

打开：

```text
http://localhost:3003/#/pages/design-preview/index
```

可通过查询参数直接打开页面：

```text
?tab=overview
?tab=personnel
?tab=quality
?tab=safety
?tab=profile
```

角色视图参数：

```text
?tab=safety&role=PROJECT_ADMIN
?tab=safety&role=SAFETY_ADMIN
?tab=safety&role=ELECTRICIAN
```

施工区域参数：

```text
?tab=overview&areaId=1
?tab=overview&areaId=4
```

## 文件说明

- `01-概况.png`
- `02-人员.png`
- `03-质量.png`
- `04-安全.png`
- `05-我的.png`
- `总览.png`
- `交互动效演示.gif`

五张页面图使用 390×844 视口生成；同时完成 375×812 响应式检查。
