# WisePenCloud Agent 协作入口

本文档是 `wisepen-cloud` 仓库的代理入口规则。开始任何代码改动前，先阅读本文件，并按任务类型继续阅读 `docs/agents/` 下的分体文档。

## 项目事实

- 本仓库是 WisePen 的 Java 后端多模块仓库。
- 构建环境使用 JDK 21 与 Maven 3.9+。
- 技术栈包括 Spring Boot、Spring Cloud Alibaba、MyBatis-Plus、Lombok、SLF4J / Logback。
- 根目录 `pom.xml` 是 Maven 聚合工程；业务服务通常拆分为 `*-api` 与 `*-biz`。

## 必读路由

- 模块职责、服务边界、跨服务调用：读 `docs/agents/module-boundaries.md`。
- Controller / Service / Mapper 写法、安全上下文、注释、命名、性能：读 `docs/agents/backend-conventions.md`。
- 日志级别、logfmt、`@Slf4j`、`@Log`、敏感信息：读 `docs/agents/logging-guideline.md`。
- 错误码、`ServiceException`、`CommonError`、各服务 `*Error`：读 `docs/agents/error-code-guideline.md`。
- `@Tag`、`@Operation.summary`、`@Operation.description`：读 `docs/agents/openapi-guideline.md`。
- API 契约、wire 类型、required/nullable、`R` / `PageR`、枚举和 breaking change：读 `docs/agents/api-contract-guideline.md`。
- 构建、测试、无法编译时的替代检查：读 `docs/agents/verification.md`。

## 最高优先级红线

- 读取中文源码和 Markdown 时显式使用 UTF-8，避免中文乱码；发现乱码先修正读取方式，不要基于乱码内容改文档。
- 不提交真实密钥、Token、数据库密码、个人环境配置或可冒充用户身份的信息。
- 不把本地 Nacos、MySQL、Redis、MongoDB、Kafka、对象存储地址硬编码进业务代码。
- 不凭空编造错误码、枚举、接口、字段或配置项；新增前先搜索现有实现。
- 写业务代码前必须先阅读同模块相邻 Controller / Service / Repository，优先继承最近代码的边界、命名、鉴权、组装和错误处理方式。
- 通用后端分层经验低于本仓库既有风格；不要为了“更通用”或“更干净”引入项目中没有先例的抽象。
- 写兜底、降级、兼容分支前必须先探索真实调用链和失败场景，确认必要性。
- 降级、catch 后返回空结果、默认值兜底必须先核查相邻先例和业务必要性；没有明确理由时不要新增。
- 修改代码时优先保持局部、清晰、可验证；不要为了“更通用”引入无明确收益的抽象。
- 如果 Maven 因环境、依赖或网络问题无法编译，不要反复尝试同类命令；记录原因后改用静态检查。

## 输出要求

- Markdown 文档使用中文书写，技术名词、命令、类名、注解、枚举、日志 key 保留英文原文。
- 总结改动时说明涉及文档、关键约束、参考过的相邻实现、与既有写法的有意偏离，以及执行过的检查。
