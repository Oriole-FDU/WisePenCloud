# WisePenCloud 模块边界

本文档约束 `wisepen-cloud` 的模块职责、服务边界和跨服务调用方式。修改业务代码前，应先确认改动属于哪个模块，不要把其他服务的实现细节搬进当前服务。

## 1. Maven 模块形态

- 根目录 `pom.xml` 是 Maven 聚合工程，统一管理版本、依赖和插件。
- `wisepen-common` 放跨服务共享的基础能力，例如统一响应、错误码基础类型、安全上下文、公共异常处理、Web 拦截器、灰度与 MQ 基础设施。
- `wisepen-common-log` 放业务审计日志能力，例如 `@Log`、日志切面和远程落库服务。
- 各业务服务通常包含 `*-api` 与 `*-biz`：
  - `*-api` 放对外契约、DTO、Base、枚举、常量、Feign 接口和 MQ 消息类型。
  - `*-biz` 放运行时实现、Controller、Service、Mapper、Repository、Consumer、Task、配置和服务内错误枚举。
- 只有没有对外 API 契约的服务可以只有 `*-biz`，但新增跨服务调用前应先补齐清晰的 API 契约。

## 2. 服务职责

- `wisepen-user-service`：用户、认证、身份验证、小组、小组成员、钱包、点卡、余额和用户展示信息。
- `wisepen-system-service`：系统邮件、用户反馈、操作日志接收和系统级辅助能力。
- `wisepen-resource-service`：统一资源主档、标签树、资源标签绑定、资源权限、互动统计、搜索同步和资源删除事件。
- `wisepen-document-service`：文档上传初始化、文档处理状态、文档解析、PDF 预览、水印和文档资源信息组合。
- `wisepen-media-service`：图片、视频和音频上传处理、预览图与视频 HLS 生成、播放授权和源文件下载；播放按媒体资源交付，不维护观看者会话。
- `wisepen-file-storage-service`：对象存储配置、上传凭证、上传回调、小文件代理上传、存储记录和文件删除事件。
- `wisepen-note-service`：笔记主档、协同笔记版本、笔记操作日志、笔记快照消费和笔记资源信息组合。
- `wisepen-ai-asset-service`：技能资产主档、技能版本、技能文件上传、技能发布和技能相关资源联动。
- `wisepen-web-app-service`：可运行 Web 资源主档、Single HTML Page 内容与版本，以及未来 Web 项目的构建和部署运行时。
- `wisepen-fudan-extension-service`：复旦 UIS 等学校扩展能力、认证任务和相关异步消息。
- `wisepen-docs-service`：API 文档聚合或文档站点相关能力。

## 3. 跨服务调用

- 跨服务调用优先使用已有 `*-api` 中的 Feign 接口、DTO、枚举和 MQ 消息类型。
- 不在当前服务复制其他服务的 Entity、Mapper、Repository 或内部 Service 实现。
- 不通过字符串拼装方式绕过现有 Feign/API 契约调用其他服务。
- 如果现有 API 契约不足，先扩展提供方的 `*-api`，再在消费方使用新契约。
- OpenAPI 文案只维护在 Controller；Feign 接口不维护独立 `@Tag` / `@Operation` 文案。

## 4. 公共能力归属

- 多个服务都会使用的基础类型、工具和自动配置放 `wisepen-common`。
- 业务审计日志相关能力放 `wisepen-common-log`。
- 只有某个业务域使用的常量、枚举、DTO、错误码留在对应服务。
- 不要因为一个服务内部重复两三次就上升为公共能力；公共能力应有稳定跨服务需求。

## 5. Review 检查清单

- 改动是否落在正确的业务服务和 `*-api` / `*-biz` 层级。
- 是否避免了把其他服务的实现类、表结构或内部规则搬进当前服务。
- 新增跨服务能力是否通过 API 契约表达，而不是通过实现细节耦合。
- 公共能力是否确实跨服务稳定复用，而不是过早抽象。
