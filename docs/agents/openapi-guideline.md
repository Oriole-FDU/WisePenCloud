# WisePen OpenAPI 接口撰写细则

本文档约束 WisePen Java 微服务中 `@Tag` 与 `@Operation` 的写法，目标是让统一 API 文档服务于联调，而不是成为 Controller 名称和路径的另一种展示。

## 1. 基本原则

- `@Tag` 表达联调人员查找接口时使用的业务能力域，不表达微服务名、Controller 类名或代码分层。
- `@Operation.summary` 使用短中文动宾结构，在不看 path 的情况下也能识别接口动作和对象。
- `@Operation.description` 写业务契约，必须说明请求如何被业务系统接受、处理和返回。
- OpenAPI 契约统一维护在 Controller 上。Feign 接口不维护 `@Tag` / `@Operation` 文案，避免与 Controller 双写漂移。
- 响应字段的 required/nullable、集合默认值和枚举 wire 值按 `docs/agents/api-contract-guideline.md` 执行；本文只约束 Controller 上的 OpenAPI 文案。
- `operationId` 推荐新增接口使用英文小驼峰，但历史接口整改不强制补齐。

## 2. Tag 规则

公共接口使用业务域名：

```java
@Tag(name = "用户认证", description = "用户登录、登出、注册与密码重置")
@Tag(name = "资源标签", description = "个人与小组资源标签的树形维护")
```

内部、管理员、外部回调接口使用访问域前缀：

```java
@Tag(name = "内部 - 资源", description = "供业务微服务注册资源、更新资源属性和校验资源权限")
@Tag(name = "管理员 - 用户", description = "管理员查询用户、维护用户状态、资料与密码")
@Tag(name = "外部 - 文件存储", description = "处理对象存储服务回调")
```

禁止用“服务”“Controller”“接口集合”表达分组。

## 3. Summary 规则

`summary` 必须是短中文动宾短语：

```java
@Operation(summary = "获取当前用户信息")
@Operation(summary = "分页查询小组成员")
@Operation(summary = "初始化文档上传")
@Operation(summary = "校验资源权限")
```

动作词优先使用：`创建`、`更新`、`删除`、`查询`、`分页查询`、`获取`、`初始化`、`刷新`、`重试`、`取消`、`上传`、`下载`、`发送`、`校验`、`切换`、`发布`、`回退`、`记录`、`设置`、`提交`、`接收`。

禁止斜杠并列、路径复述、技术实现或调用方描述。

内部接口可以在 summary 中保留“内部”，用于在统一文档中快速识别调用边界。

## 4. Description 规则

`description` 不是 summary 的扩写，而是联调契约。编写前必须先读请求参数、权限判断、service 调用和相邻接口边界。

固定字段必须按以下顺序书写，并保留每行开头的 `- `。统一文档展示依赖该格式，不要改成无横线字段：

```text
- 用途：
- 请求：
- 约束：
- 处理：
- 失败：
- 响应：
```

字段含义：

- `用途`：接口处于哪条业务链路、完成什么业务任务，不复述 summary。
- `请求`：关键入参、字段域、组合语义、可选字段和未传字段含义。
- `约束`：身份、角色、归属、状态、唯一性、目标对象存在性等会导致拒绝请求的条件。
- `处理`：系统接受请求后实际创建、更新、删除、查询、跨服务调用或异步提交了什么，并明确不会做什么。
- `失败`：只写联调容易踩到且当前接口会触发的失败点，必须使用 `触发条件 -> ErrorEnum.CONSTANT` 格式；`ErrorEnum.CONSTANT` 必须是真实存在的错误枚举常量；不写笼统的统一返回说法，不写没有枚举名的权限、校验或系统失败；参数校验默认不写，除非接口显式把参数转换为业务错误；未捕获异常只在接口确实存在明确兜底风险时写 `触发条件 -> CommonError.INTERNAL_ERROR`。
- `响应`：返回值的业务含义；空返回可以简写为“成功时返回空结果”。

所有 Controller 映射方法都必须写 `summary` 和结构化 `description`。简单查询也要写清请求、约束、处理和响应，避免联调方通过猜测理解接口。

示例：

```java
@Operation(
        summary = "重命名资源",
        description = """
                - 用途：维护资源在列表、详情和搜索场景中的展示名称。
                - 请求：resourceId 指定目标资源；name 是新的资源名称。
                - 约束：当前用户必须是资源所有者；目标资源必须存在。
                - 处理：更新资源主记录的名称并同步搜索索引；不修改资源类型、标签挂载、可执行动作或归属关系。
                - 失败：目标资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；搜索索引同步失败 -> ResourceError.RESOURCE_SEARCH_FAILED。
                - 响应：成功时返回空结果。
                """
)
```

## 5. Review 检查清单

- `@Tag` 是否能让联调人员稳定定位接口。
- 内部、管理员、外部接口是否使用 `访问域 - 业务域` 格式。
- `summary` 是否是不看 path 也能理解的短中文动宾短语。
- `description` 是否保留 `- 用途：`、`- 请求：`、`- 约束：`、`- 处理：`、`- 失败：`、`- 响应：` 格式，并写清“谁能调、传什么、约束是什么、系统做什么、不会做什么、失败点和响应含义”。
- `失败` 字段引用的错误枚举是否真实存在。
- Feign 接口是否没有维护独立 OpenAPI 文案。
