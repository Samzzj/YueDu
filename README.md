# 悦读 (YueDu) · Android v0.1.0

极简纯净的 Android TXT 阅读器，采用 **iOS 27 Liquid Glass（液态玻璃）** 视觉语言打造。

## 第一版已实现功能

- **TXT 纯文本阅读引擎**
  - 自动识别 `UTF-8`、`UTF-16`、`GB18030 / GBK` 编码，告别中文乱码
  - 自动匹配中英文章节目录，无目录长文自动分节，大文件流式转码秒开
  - 基于 `StaticLayout` 精准分页，支持**左右翻页**与**上下连续滚动**
  - 内置纯净白、暖羊皮、护眼青、深空黑 4 款主题，字号/行距调节不丢进度
- **同 Wi-Fi 电脑传书**
  - 手机端内嵌轻量 HTTP 服务，电脑浏览器打开局域网地址即可拖拽批量上传 `.txt` 书籍与字体
- **自定义字体导入**
  - 支持从手机本地或电脑 Wi-Fi 导入 `.ttf / .otf / .ttc` 字体，阅读器内一键切换预览
- **Liquid Glass 界面**
  - 悬浮胶囊底部导航栏、通透高光玻璃卡片、沉浸式阅读悬浮控制栏

## 构建方式

推送至 GitHub 后会自动触发 GitHub Actions (`Build YueDu Android APK`) 编译出 `YueDu-v0.1.0-debug.apk` 并发布到 **Releases** 与 **Actions Artifacts**。
