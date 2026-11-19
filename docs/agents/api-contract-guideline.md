# WisePenCloud API 契约规范

本文档约束前后端及服务间 HTTP 契约的 wire 类型、required/nullable 语义、枚举值和变更方式。`@Tag`、`@Operation.summary`、`@Operation.description` 的写法见 `docs/agents/openapi-guideline.md`；本文件只处理可被前端消费的 schema 契约。

## 1. 核心原则

- API 契约是外部可观察行为，包括 method、path、鉴权、请求/响应 wire schema、错误码和枚举值，不等同于 Java DTO 类本身。
- 响应字段必须能被前端判定为“必填且非空”或“必填但可空”；响应 DTO 不允许出现“字段可能不存在”的模糊状态。
- 前端只消费明确的契约；无法从后端契约确定的兜底、默认值和类型转换，不算业务逻辑。
- 契约变更必须能被人看见；不能依赖联调、运行时 400/500 或前端兜底来发现字段变化。
- CI 只负责执行已经写清楚的规则；规则本身以本文档和同目录约定为准。

## 2. 名词规定

### 2.1 API 契约

API 契约描述调用方可以依赖的全部外部行为，包括：

- HTTP method、path、鉴权注解和安全头。
- 请求体、查询参数、路径参数的名称和 wire 类型。
- 响应外层 `R` / `PageR` 和响应 DTO 的字段、required/nullable 语义。
- 错误码、错误 `key`、错误 `msg` 的含义。
- 枚举在 body、query、OpenAPI 和数据库映射中的值。

### 2.2 Wire Type

Wire Type 是实际出现在 JSON 中的类型，例如 `string`、`number`、`boolean`、`array`、`object` 和 `null`。Java 类型不等同于 Wire Type，例如 Java `Long` 当前会被序列化为 JSON `string`。

### 2.3 required、nullable、optional

- **required**：字段始终出现在响应中。
- **nullable**：字段始终出现，但值可以为 `null`。
- **optional**：字段可能不存在。只允许用于请求参数或请求 DTO 中真正可选的字段。
- **non-null**：字段 required 且值不能为 `null`。
- **non-empty**：字符串、集合或数组不仅非 `null`，长度也不能为 0。

“必填”只表示字段存在，不自动表示值非空。需要同时表达“必填且非空”时，必须显式使用 non-null 规则。

### 2.4 DTO、Entity、Domain Model 和 Mapper

- **API DTO**：只表达 HTTP 传输结构，放在对应服务的 `*-api` 或 `dto/req`、`dto/res` 下。
- **Entity**：只表达持久化结构，不得作为 Controller 返回值或进入 OpenAPI。
- **Domain Model**：前端 mapper 归一化后的业务模型，不等于后端 DTO。
- **Mapper / Assembler**：负责 Entity、DTO、Domain Model 之间的转换；契约兜底只能集中在这里，并且必须有明确原因。

### 2.5 Contract Snapshot

Contract Snapshot 是从后端代码生成并提交到仓库的 OpenAPI JSON。它用于 PR diff、breaking change 检查和前端类型生成，是前后端共同消费的版本化契约。

### 2.6 Breaking Change

以下变化默认视为 breaking change：

- 删除或重命名字段、接口、错误码或枚举值。
- 改变字段的 Wire Type，例如 `string` 改为 `number`。
- 响应字段从 required 改为 optional，或从 non-null 改为 nullable。
- 请求字段从 optional 改为 required。
- 枚举的 body、query 或 OpenAPI wire 值发生不兼容变化。

新增可选字段通常不是 breaking change；新增必填请求字段、删除响应字段或改变分页/时间/ID 语义必须经过契约评审。

## 3. 全局 Wire 类型

- **ID**：wire 类型为 `string`。Java 侧可以继续使用 `Long` 并依赖全局序列化，也可以使用 `String`，但前端 DTO 直接声明为 `string`。
- **计数、版本、页码、页大小**：wire 类型为 `number`。新增字段优先使用 `Integer` / `int`，不要新增 `long` 计数。
- **时间**：`LocalDateTime` 序列化为毫秒时间戳 `number`；`LocalDate` 保持 `YYYY-MM-DD` 字符串；禁止用 `Long` 表示时间戳。
- **布尔**：wire 类型为 `boolean`。
- **集合**：响应集合默认返回空数组，不能返回 `null`；Map 默认返回空对象，不能返回 `null`。
- **分页 `total`**：使用 `number`。禁止前端声明为 `string` 或 `string | number` 的联合类型；如需调整实现，应改 `PageR` 字段或增加字段级 number serializer，而不是把全局 `Long -> string` 泄漏到分页总数。

Java `Long` 的全局序列化是当前兼容策略，不是所有计数字段的长期设计。新增字段必须先判断语义，再选择 Java 类型和 Wire Type。

## 4. 响应 DTO

- 响应字段只允许两种语义：required 且 non-null，或 required 但 nullable。不得使用 optional。
- required 使用 `@Schema(requiredMode = Schema.RequiredMode.REQUIRED)` 声明。
- nullable 使用 `@Schema(nullable = true)` 声明。
- 同时 required 且 nullable 的字段，两个声明都要写。
- 不要使用 `@JsonProperty(required = true)` 代替响应契约声明；它主要影响反序列化，不能完整表达响应 schema。
- 不要全局使用 `@JsonInclude(JsonInclude.Include.NON_NULL)`。否则“字段不存在”和“字段为 null”会再次混在一起。
- 响应 DTO 不暴露 Entity 字段、逻辑删除字段、密码、内部状态或仅供持久化使用的字段。

## 5. `R` 和 `PageR`

### 5.1 `R<T>`

目标契约：

- `code`：required `number`。
- `key`：required `string`。
- `msg`：required `string`。
- `data`：始终存在，类型为 `T | null`。
- `R<Void>` 的 `data` 固定为 `null`，前端不要使用 `void` 或 `undefined` 表示成功响应。

成功响应应使用固定非空的 `key` 和 `msg`。现有 `R.ok()` 返回 `null` 的实现属于待迁移项，新增接口不得继续把 null 当作成功响应契约。

### 5.2 `PageR<T>`

- `list`：required、non-null 数组；无数据时为 `[]`。
- `total`：required `number`。
- `page`：required `number`。
- `size`：required `number`。
- `totalPage`：required `number`。

## 6. 请求 DTO

- 所有带校验注解的 `@RequestBody` 必须使用 `@Valid` 或 `@Validated` 触发校验。
- ID 字段使用 `@NotNull` 或 `@NotBlank`；集合参数使用 `@NotEmpty`，不能只使用 `@NotNull`。
- 枚举字段必须与 query 参数使用同一套 wire 值；不能 body 接收数字、query 按枚举名绑定。
- 部分更新必须明确规则：未传字段表示不更新，还是表示清空；不能同时承诺两种相反语义。
- 如果无法区分“未传”和“显式 null”，应拆成独立接口或使用显式 clear 标记，不要用 null 兼作两种含义。

## 7. 枚举

- 一个枚举只能有一套对外 wire 值。body、query、OpenAPI、前端类型必须一致。
- 新增枚举优先使用字符串值，例如 `STUDENT`、`TEACHER`，避免数字值和枚举名混用。
- 存量数字枚举如果暂不迁移，必须为 `@RequestParam` 增加 `ConverterFactory` 或显式解析，并补充集成测试。
- 不得依赖 Spring 默认的枚举名绑定来推断数字枚举的运行时行为。
- 历史兼容值必须在契约中写明，例如 `MessageType.GROUP` 的 wire 值不是枚举名 `GROUP` 时，必须在前端类型和文档中明确标注。

## 8. Controller 与跨服务契约

- Controller 只能返回 `R`、`PageR` 或专用 Response DTO，不得返回 Entity。
- Controller 不暴露数据库字段、逻辑删除字段、密码字段或内部审计字段。
- `Map`、`Object`、原始字符串等宽松返回类型只允许用于外部回调或协议适配，并必须在 OpenAPI 中写清结构。
- 跨服务 Feign 调用优先使用对应 `*-api` 中已有的 DTO、枚举和 Feign 接口；不得复制其他服务的 Entity、Mapper 或内部实现。
- `*-api` 中的 DTO 是对外契约的一部分，修改时必须评估所有 `*-biz` 消费方。

## 9. 前端消费规则

- 响应 DTO 的 ID 字段直接使用 `string`，计数、版本、分页数字直接使用 `number`，时间直接使用 `number`。
- 必填响应字段使用 `T`，真实可空字段使用 `T | null`；响应 DTO 不使用 `?:`。
- API 类型层不使用 `Nullable<T>`、`JavaLongApiValue`、`NumericEnumApiValue` 或 `string | number` 来表达契约不确定性。
- API 层不做 fallback、枚举转换、时间格式化或空字符串兜底；归一化集中在 mapper。
- 组件层只处理真实的 loading、error 和业务可空状态，不承担后端契约猜测。

## 10. 契约变更流程

- 修改 DTO、枚举、`R`、`PageR` 或错误码时，先判断是否属于 breaking change。
- 非 breaking change 可以直接提交；breaking change 必须同步通知前端或消费方，并在同一个发布窗口内完成迁移。
- 优先采用“新增字段并标记 deprecated，再迁移，最后删除”的方式；不要在未确认消费方的情况下直接删除字段。
- Contract Snapshot 接入后，任何响应 schema 变化都应在 PR 中显示 diff；breaking diff 必须经过确认。
- 不维护两套长期并行的 wire 表示；兼容层必须有明确的删除条件。

## 11. CI 与验证

当前后端 CI 在 PR 和 `main` push 时执行：

```bash
./mvnw -B -ntp verify
```

该命令负责编译、资源处理、测试和 Maven 生命周期校验。PR 质量门禁由 GitHub Actions 负责；Jenkins 继续负责镜像构建与部署，部署流水线不能以 `-Dmaven.test.skip=true` 作为唯一验证。

GitHub 仓库需要把 `Backend CI / Maven Verify` 配置为 `main` 分支的 required check；仅提交 workflow 文件不会自动启用分支保护。

后续接入 Contract Snapshot 后，CI 再增加 OpenAPI 生成、Spectral lint、`oasdiff breaking` 和前端类型同步检查。未接入前，不应在文档或 PR 中声称这些检查已经生效。

## 12. Review 检查清单

- 响应 DTO 的每个字段是否明确为 required 且 non-null，或 required 但 nullable。
- 集合字段是否默认返回空数组/空对象，而不是 `null`。
- `Long` 是否只用于 ID 或计数，是否误用为时间戳。
- `PageR.total` 是否为 `number`，是否出现 `string | number`。
- `R<Void>` 的 `data` 是否按 `null` 处理。
- 枚举在 body、query、OpenAPI 和前端类型中是否一致。
- 带校验注解的 `@RequestBody` 是否有 `@Valid` 或 `@Validated`。
- Controller 是否只返回 DTO，是否暴露了 Entity 或内部字段。
- 部分更新的“未传”和“显式 null”语义是否写清楚。
- 新增兜底、默认值或 catch 后返回空结果是否有明确业务理由。
