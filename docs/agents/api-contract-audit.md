# WisePenCloud API 契约审计

本文档记录 2026-10-20 的 API 契约基线、问题分类和迁移顺序。它不替代 `api-contract-guideline.md` 的规则，只回答两个问题：现在有多少存量问题，以及哪些问题应当一起改。

## 1. 审计口径

- 范围：各 `*-api` 模块 `dto/res` 下的响应 DTO、Controller 直接返回对象，以及 `R`、`PageR`、公共枚举和 Jackson 配置。
- 请求侧同时审计公开 Controller 的 `@RequestBody` 校验入口，不把 Feign 接口、内部接口和第三方回调简单等同于公开接口。
- 字段数量按 DTO 文件内的显式字段声明统计，不含父类字段；实际对前端暴露的字段面比下表更大。
- 前端统计以 `src/domains/**/apis/*.type.ts` 为边界，不把 mapper、领域模型和组件内部类型混入 API 契约。
- 本文只记录可由代码直接确认的问题。`nullable` 和集合是否可空仍需结合 Service 赋值逻辑逐个确认，不能只凭字段名推断。

## 2. 基线快照

### 2.1 后端响应 DTO

| 服务 | 响应 DTO 文件 | 显式字段 |
| --- | ---: | ---: |
| `user` | 18 | 93 |
| `resource` | 19 | 74 |
| `ai-asset` | 8 | 22 |
| `document` | 5 | 15 |
| `note` | 5 | 13 |
| `system` | 1 | 0（字段全部继承自 `SysOperLogBase`） |
| **合计** | **56** | **217** |

- 只有 2 个响应 DTO 使用 `@Schema`，即 `UserAdminListItemResponse` 和 `UserAdminDetailResponse`；其余 54 个没有声明 required/nullable。
- 现有 `@Schema` 共 26 处，其中 `requiredMode = REQUIRED` 26 处、`nullable = true` 20 处，全部集中在上述 2 个 DTO。
- 显式字段中包含 27 个 `LocalDateTime` 字段、13 个 `Long` 字段；其中 `ResourceCommentItemResponse.createTime` 仍用 `Long` 表示时间。
- 显式字段中包含 24 个集合字段，只有 5 个在声明处给了空集合默认值。

### 2.2 后端请求入口

- `@RequestBody` 共 112 处，其中 89 处同一行带 `@Valid` 或 `@Validated`。
- 余下 23 处并不都是缺陷：Feign 接口、内部服务接口、OnlyOffice 回调、对象存储回调不应机械补 Bean Validation。
- 已确认缺失校验的公开业务入口有 4 个：
  - `UserController.updateUserProfile`
  - `UserController.updateUserInfo`
  - `WalletController.redeemVoucher`
  - `GroupResConfigController.upsertConfig`
- 需要分类处理但不应直接按公开接口处理的入口包括 `InternalSkillController`、`InternalResourceItemController`、`InternalStorageController`、`OnlyOfficeController`、`ExternalStorageController`、`MailController`、`RemoteLogController` 及对应 Feign 接口。

### 2.3 枚举

- 只有 3 个枚举的 `@JsonValue` 返回数字：`IdentityType`、`GroupType`、`GroupRoleType`。
- 目标 wire 值分别为 `STUDENT/TEACHER/ADMIN`、`NORMAL_GROUP/ADVANCED_GROUP/MARKET_GROUP`、`OWNER/ADMIN/MEMBER/NOT_MEMBER`。
- `GroupType`、`IdentityType` 会出现在 query 参数中；body 和 query 必须共用同一套 canonical 值。
- `GroupRoleType` 会通过 `Map<Long, GroupRoleType>` 在服务间传输，迁移时必须保留对旧数字 code 的短期解析能力。
- `MessageType.GROUP` 的 wire 值是 `"NORMAL"`；前端不能用枚举常量名反推 wire 值。

### 2.4 前端 API 类型

对 `src/domains/**/apis/*.type.ts` 做 TypeScript AST 临时统计，得到：

| 类型 | 接口数 | 字段数 | `?:` 字段 | 可空字段 |
| --- | ---: | ---: | ---: | ---: |
| response-like | 117 | 617 | 352 | 218 |
| request-like | 226 | 703 | 292 | — |

- 另有 26 个标量联合类型、1 处 `NumericEnumApiValue`、17 处 `JavaLongApiValue`。
- 高频问题文件包括 `TagApi.type.ts`、`InlineCommentApi.type.ts`、`AdminUserApi.type.ts`、`GroupApi.type.ts`、`ResourceApi.type.ts`。
- `PageR.total` 在后端是 `long`，当前全局序列化为 string，而前端声明为 `number`。这是已确认的 wire 类型错误。
- `AdminUserApi.type.ts` 仍使用 `status`、字符串时间戳和全 optional 模型，与当前管理员用户响应 DTO 不一致。
- `DocumentApi.type.ts` 把 `PendingDocItemApiResponse.documentId` 声明为 optional，但前端重试、取消和同步都依赖该字段。
- `AuthApi.type.ts` 把注册响应声明为 `string | undefined`，后端实际返回 `R<Void>`。

## 3. 必须一起收紧的类型

### 3.1 统一响应壳 `R<T>`

- `code`、`key`、`msg`、`data` 都应 required；其中 `key`、`msg`、`data` 当前仍可能为 null。
- `R<Void>` 的 `data` 应显式标为 required + nullable。
- 成功响应的 `key`、`msg` 长期目标是非空字符串，但这会改变现有前端 envelope 校验，应与前端同一窗口迁移。

### 3.2 分页 `PageR<T>`

- `list` 必须非空数组，无数据时返回 `[]`。
- `total` 的 wire 类型必须是 `number`。当前全局 `long -> string` 不应泄漏到分页总数，需在 `PageR.total` 上做字段级 number 序列化。
- `page`、`size`、`totalPage` 必须是 `number`。

### 3.3 ID、计数、版本和时间

- ID：wire 类型为 `string`，前端 API DTO 直接声明 `string`，不再使用 `string | number`。
- 计数、版本、页码：wire 类型为 `number`；新增字段优先使用 `Integer`/`int`。
- 时间：`LocalDateTime` 序列化为毫秒时间戳 `number`，`LocalDate` 为 `YYYY-MM-DD` 字符串；禁止用 `Long` 表示时间。
- 布尔：wire 类型为 `boolean`，不使用 `0/1`。

### 3.4 枚举

- 对外枚举统一为 canonical string。
- 3 个数字枚举迁移时同时补齐：
  - `@JsonValue` 返回字符串；
  - `@JsonCreator` 接受 canonical string 和短期旧数字 code；
  - query 参数使用统一转换器；
  - body/query 各至少一个 canonical 值和一个非法值测试。
- OpenAPI 只展示 canonical 值，旧数字 code 不进入 schema。

### 3.5 响应字段语义

- 每个响应字段必须恰好属于“required + non-null”或“required + nullable”。
- 响应 DTO 禁止 optional；字段默认由 Jackson 完整输出，不使用 `NON_NULL` 隐藏字段。
- 集合响应默认空集合；Map 默认空对象。
- `@NotNull`、`@NotBlank`、`@NotEmpty` 不得代替响应 DTO 的 `@Schema`。

### 3.6 请求入口

- 带校验注解的公开 `@RequestBody` 必须有 `@Valid` 或 `@Validated`。
- 部分更新必须写清“未传”和“显式 null”的语义，不能一边声称空字段不更新，一边允许 null 清空。
- 内部、回调和 Feign 接口按真实调用方单独判断，不把 Bean Validation 当作所有入口的默认答案。

### 3.7 Controller 返回类型

- Controller 只能返回 `R`、`PageR` 或专用 Response DTO。
- Controller 不得返回 Entity 或 Entity 继承链上的 Base。
- 已知问题：`DocumentController.listPendingDocs` 直接返回 `List<DocumentVersionBase>`，而实际数据是 `DocumentVersionEntity`；前端依赖的 `documentId` 不在 Base 中。

### 3.8 前端 API 边界

- API DTO 只描述真实 wire 形状，不承担兜底。
- 响应字段使用 `T` 或 `T | null`，不使用 `?:`。
- 删除 `Nullable<T>`、`JavaLongApiValue`、`NumericEnumApiValue` 和 `string | number` 对契约不确定性的表达。
- 所有归一化集中在 mapper；组件不判断“后端可能没返回”。
- 后端契约缺失时先修后端或显式记录，不在前端加兼容 union。

## 4. 存量 DTO 清单

### 4.1 user（18）

`GroupDashboardActorMetricResponse`、`GroupDashboardResourceMetricResponse`、`GroupDetailInfoResponse`、`GroupItemInfoResponse`、`GroupMemberDetailResponse`、`GroupMemberTokenDetailResponse`、`GroupResourceDailyMetricResponse`、`MessageInfoResponse`、`UserAdminDetailResponse`、`UserAdminListItemResponse`、`UserDetailInfoResponse`、`UserInviteRecordResponse`、`UserSearchUserResponse`、`UserTaskCheckInResponse`、`UserTaskStatusResponse`、`UserTourStatusResponse`、`WalletDetailResponse`、`WalletTransactionRecordResponse`。

### 4.2 resource（19）

`FavoriteCollectionResponse`、`FavoriteItemResponse`、`GroupResConfigResponse`、`InlineCommentItemReactionGroupResponse`、`InlineCommentItemResponse`、`MarketOrderResponse`、`MarketSaleInfoResponse`、`MarketSaleTierResponse`、`MarketSearchHitItemResponse`、`ResourceBaseInfoResponse`、`ResourceCommentItemResponse`、`ResourceFavoriteStatusResponse`、`ResourceInlineCommentResponse`、`ResourceItemResponse`、`ResourcePlacementResponse`、`ResourceTagBindResponse`、`ResourceUserInteractionRecordResponse`、`SearchHitItemResponse`、`TagTreeResponse`。

### 4.3 ai-asset（8）

`AIResourceMetaInfoResponse`、`AgentInfoResponse`、`AgentResourceInfoResponse`、`AgentVersionBundleInfoResponse`、`AssetUploadInitResponse`、`SkillInfoResponse`、`SkillResourceInfoResponse`、`SkillVersionBundleInfoResponse`。

### 4.4 document（5）

`DocumentInfoResponse`、`DocumentSearchTextResponse`、`DocumentUploadInitResponse`、`DocumentVersionInfoResponse`、`OnlyOfficeEditorConfigResponse`。

### 4.5 note（5）

`NoteInfoResponse`、`NoteOperationLogResponse`、`NoteSearchTextResponse`、`NoteSnapshotResponse`、`NoteVersionInfoResponse`。

### 4.6 system（1）

`SysOperLogResponse`（字段继承自 `SysOperLogBase`）。

## 5. 高优先级缺陷

| 优先级 | 问题 | 结论 |
| --- | --- | --- |
| P0 | `PageR.total` 后端 string、前端 number | 修后端字段级 number 序列化，前端保持 number |
| P0 | 3 个数字枚举 | body、query、Feign、OpenAPI、前端类型同一窗口迁移 |
| P0 | 公开入口缺 `@Valid` | 修 4 个公开业务入口；内部/回调单独分类 |
| P0 | `DocumentController.listPendingDocs` 返回 Entity Base | 新增专用 DTO，补 `documentId`、`version` |
| P1 | 54 个响应 DTO 缺 required/nullable | 按模块逐字段核对 Service 赋值后补 |
| P1 | 24 个集合字段缺默认值 | 先确认业务可空性，再统一空集合 |
| P1 | `ResourceCommentItemResponse.createTime` 为 `Long` | 改 `LocalDateTime` |
| P1 | 前端响应类型大量 optional/union | 与后端 schema 对齐，归一化留在 mapper |
| P2 | `AdminMessageApi` 调用不存在的 `/admin/message/*` | 需产品确认是补后端还是下线页面，不能静默删除 UI |
| P2 | `R.ok()` 的 `key/msg` 仍为 null | 前后端同一窗口收紧为非空字符串 |

## 6. 迁移顺序

1. 修 `PageR.total`、`R` 字段声明和 Jackson 契约测试。
2. 完成 3 个数字枚举的 body/query/Feign/OpenAPI/前端迁移。
3. 修公开入口 `@Valid`、`DocumentController.listPendingDocs` 和已知时间字段。
4. 按 `user -> resource -> document/note -> ai-asset/system` 逐模块补响应字段 required/nullable 和集合默认值。
5. 前端同步 API DTO、mapper、mock，删除数字枚举和分页 total 的兼容类型。
6. 接入 OpenAPI snapshot 后，用 diff 锁定 wire 类型、required/nullable 和枚举值。

## 7. 不在本轮自动处理的内容

- 不恢复历史 PR 中的大范围运行期 assert；本次优先用 schema、TypeScript 类型和 mapper 边界收口。
- 不对所有 `@RequestBody` 机械补 `@Valid`；内部接口、Feign 接口和第三方回调必须按调用方分类。
- 不全局启用 `@JsonInclude(NON_NULL)`；字段缺失与字段为 null 必须可区分。
- 不根据字段名猜测 nullable；必须查看 Service/Assembler 的真实赋值路径。
- 不在无法确认消费方时直接删除响应字段或前端页面。

## 8. 复现命令

```bash
rg --files | rg '/dto/res/.*\.java$' | sort
rg -n '@Schema|RequiredMode\.REQUIRED|nullable\s*=\s*true' <响应 DTO 文件>
rg -n '@RequestBody' --glob '*.java'
rg -n '@JsonValue' --glob '*.java'
rg -n 'NumericEnumApiValue|JavaLongApiValue|string \| number' src/domains --glob '*.ts'
```
