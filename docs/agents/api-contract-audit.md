# WisePenCloud API 契约审计

本文记录 2026-10-20 的 API 契约基线、已完成整改和剩余例外。它不替代 `api-contract-guideline.md`，只回答当前还有哪些类型需要继续收紧，以及哪些 `nullable` 是业务语义而不是历史兜底。

## 1. 审计口径

- 范围：各 `*-api` 模块的 `dto/res`、响应继承的 `domain/base`、Controller 直接返回对象、`R`、`PageR`、公共枚举和 Jackson 配置。
- 请求侧只审计公开业务入口的 `@RequestBody` 校验；Feign、内部服务接口和第三方回调单独分类。
- 响应字段必须落在“必填非空”或“必填可空”两种语义内，不使用 optional。
- `List`、`Set`、`Map` 默认空集合；只有明确表达“未配置/继承默认值”的嵌套策略对象允许继续保留 null。
- 本文不根据字段名猜 nullable，结论均来自 Service / Assembler / Controller 的真实赋值路径。

## 2. 当前基线

### 2.1 后端响应 DTO

| 服务 | 响应 DTO 文件 |
| --- | ---: |
| `user` | 18 |
| `resource` | 19 |
| `ai-asset` | 8 |
| `document` | 6 |
| `note` | 5 |
| `system` | 1 |
| **合计** | **57** |

- 57 个响应 DTO 共 221 个显式字段，已有 229 处 `@Schema` 注解；响应 DTO 和响应 Base 的字段均声明了 required/nullable。
- 所有响应 `Long/long` 字段都显式声明 `type = "string"`；`Integer/int` 保持 JSON number。
- 所有响应 `LocalDateTime` 都声明为毫秒时间戳 number；`LocalDate` 保持 ISO 日期 string。
- 23 个响应 DTO 集合字段全部在声明处默认空集合。
- 剩余 2 个集合 null 位于 `AgentSpecInfoBase.AgentToolAndSkillPolicy`：`toolSelectionOverrides` 和 `onDemandSkillIds`。它们同时用于请求和响应，null 表示“继承 Agent 默认策略”，暂不作为普通响应集合收紧。

### 2.2 统一响应壳与分页

- `R.ok()` 固定返回 `key = "SUCCESS"`、`msg = "操作成功"`，不再返回 null。
- `R<Void>.data` 为 required + nullable。
- `PageR.total` 使用字段级序列化器输出 JSON number；`list` 默认空数组。
- 后端已有 `ApiContractSerializationTest` 覆盖 envelope、分页和枚举 wire 值。

### 2.3 枚举

- `IdentityType`、`GroupType`、`GroupRoleType` 的 wire 值已迁移为 canonical string。
- 三个枚举均有 `@JsonCreator`，query 参数通过 `WisepenWebAutoConfiguration` 的 `Converter` 解析。
- 迁移窗口内仍接受旧数字 code，但 OpenAPI 只应展示 canonical string。
- 其余枚举的 `@JsonValue` 均返回字符串；`MessageType.GROUP` 的 wire 值仍是 `"NORMAL"`。

### 2.4 请求校验

- 公开业务入口的 `@RequestBody` 已补 `@Valid` / `@Validated`。
- 仍未加校验的 `@RequestBody` 仅位于 Feign、内部服务、OnlyOffice 回调和对象存储回调，属于有意保留。

### 2.5 前端 API 类型

- 已删除 `NumericEnumApiValue`、`JavaLongApiValue`，API 类型不再用 union 表达 Java Long 或数字枚举兼容。
- `PageR.total` 在前端保持 number，与后端 contract serializer 一致。
- Java Long ID、计数和资源动作枚举已按 canonical string / number 收紧。
- Java 服务响应侧 optional 仅剩 `AdminMessageApiModel`；该 API 调用不存在的 `/admin/message/*`，属于孤儿接口，不能在没有产品确认时直接删 UI。
- Python Cloud-AI 的 `ChatApi.type.ts` 仍有外部契约 optional 字段，需在 Cloud-AI 侧单独收紧，不能由 Java 仓库单方面修改。
- `check:api-contract` 已静态扫描 `src/domains/**/apis/*.type.ts`：响应接口禁止 optional，并禁止 `string | number` 与 Long/数字枚举兼容别名；孤儿接口和 Python 契约使用显式白名单。

## 3. 本轮已完成

- `R.ok()` 的 `key/msg` 稳定非空，并补序列化测试。
- `PageR.total` 输出 JSON number，并补契约测试。
- 3 个数字枚举迁移为 canonical string，补 body/query 测试。
- 用户、钱包、小组资源配置公开入口补 `@Valid`。
- `DocumentController.listPendingDocs` 改为返回 `PendingDocumentResponse`，不再暴露 Entity/Base。
- 小组成员数改为批量真实统计，不再依赖历史 `memberCount`。
- `DocumentInfoResponse`、`NoteInfoResponse` 的作者展示 Map 在远程查询失败时返回空 Map。
- `DocumentUploadMeta.uploaderId` 收紧为必填非空；`uploaderGroupRoles` 默认空 Map。
- 钱包 `operatorId`、`traceId` 保持 required + nullable，wire 类型与其他 Long 一致为 string。
- 资源详情中非所有者不再收到 null 权限列表，而是空数组；集市未配置预览动作时返回空数组。
- file-storage、campus、user、resource、ai-asset、document、note 等响应 DTO 已补齐 `@Schema`。
- 前端同步 API DTO、mapper、mock，并新增 response envelope 门禁：keyed 响应的 `key/msg` 必须非空，Python 无 key 响应单独兼容；同时禁止响应 DTO optional 和兼容型 Long/数字枚举别名。

## 4. 已确认的 nullable 决策

- `DocumentInfoResponse.documentVersionInfo`：版本号为 0 时确实没有可展示版本，保持 required + nullable；前端 mapper 遇到 null 抛明确客户端错误，不伪造空版本对象。
- `ResourceItemResponse.ownerInfo`：远程用户展示查询失败时允许 null，前端 mapper 统一降级为最小展示对象。
- `AssetInfoBase.objectKey`：上传未完成时允许 null。
- `WalletTransactionRecordBase.traceId/operatorId`：历史流水与系统流水允许缺失，保持 required + nullable。
- `ResourceItemResponse.myInteractionRecord`、`MarketSaleInfoResponse.auditAt/auditMessage/offerVersion` 等字段有明确业务缺失态，保持 required + nullable。

## 5. 剩余事项

| 优先级 | 事项 | 处理方式 |
| --- | --- | --- |
| P1 | `AdminMessageApi` 调用不存在的 `/admin/message/*` | 需要产品确认补后端管理消息接口或下线页面，不能静默删除 |
| P1 | `AgentSpecInfoBase` 两个策略集合仍可 null | 与 Agent 默认策略继承语义绑定，拆分请求/响应 DTO 后再收紧 |
| P1 | Python Cloud-AI 响应仍有 optional | 在 Cloud-AI 契约中统一 required/nullable 后，再同步 `ChatApi.type.ts` |
| P1 | 尚未接入 OpenAPI snapshot diff | 生成各服务 OpenAPI 快照，锁定 wire 类型、required/nullable 和枚举值 |

## 6. 后续顺序

1. 处理 `/admin/message` 孤儿接口，确定补后端还是下线页面。
2. 为 Agent 策略拆分请求/响应 DTO，消除响应集合 null。
3. 接入 OpenAPI snapshot，把 schema 漂移变成 CI 失败。
4. Python Cloud-AI 契约完成后，收紧 `ChatApi.type.ts` 与 mapper，并删除对应门禁白名单。

## 7. 复现命令

```bash
rg --files | rg '/domain/dto/res/.*\.java$' | sort
rg -n '@Schema|RequiredMode\.REQUIRED|nullable\s*=\s*true' <响应 DTO 文件>
rg -n '@RequestBody' --glob '*.java'
rg -n '@JsonValue' --glob '*.java'
rg -n 'NumericEnumApiValue|JavaLongApiValue|string \| number' src/domains --glob '*.ts'
pnpm check:api-contract
```
