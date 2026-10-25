# WisePenCloud API 契约规范

本文档约束对外 Response DTO 的字段语义和枚举 wire 值。目标是让 OpenAPI 能直接表达前端需要的类型，不再依赖 `Nullable<T>`、`string | number`、`?.xxx ?? ''` 这类兜底。

## 1. 适用范围

- 适用于所有 `*-api` 模块中的 Response DTO、Controller 直接返回的对象，以及 `R`、`PageR` 等响应包装。
- Request DTO 的可选字段和校验规则仍按 `backend-conventions.md` 执行，不套用本文的响应字段规则。
- 响应字段只允许两种语义：
  - 必填非空：字段始终存在，且值不为 null。
  - 必填可空：字段始终存在，但值可以为 null。
- 响应字段不允许“字段可能不存在”的 optional 语义。

## 2. 规则一：响应字段必须声明 required/nullable

所有响应 DTO 字段都必须使用 `@Schema` 声明契约语义。

必填非空字段：

```java
@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
private String resourceId;
```

必填可空字段：

```java
@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "资源描述")
private String description;
```

约束：

- `required` 表示字段始终出现，`nullable` 表示值可以为 null，两者是不同维度。
- 可空字段仍必须保留 `requiredMode = REQUIRED`。只写 `nullable = true` 不能表达“字段始终存在”，OpenAPI 仍可能把字段当成可省略。
- 不使用 `@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)` 标注响应字段；响应不允许 optional。
- 不在响应 DTO 上用 `@NotNull`、`@NotBlank`、`@NotEmpty` 代替 `@Schema`。响应对象默认不经过 Bean Validation，这些注解不会保证输出非空，反而会让契约语义失真。
- `int`、`long`、`boolean` 等基本类型不能为 null。如果 null 有业务含义，必须改用包装类型。
- 继承字段在声明该字段的父类上标注；嵌套静态 DTO 同样适用。
- 字段说明使用中文，技术名词、类名、枚举和 JSON key 保留英文原文。

## 3. 规则二：null 语义、集合默认值和包装类型

- 响应字段只允许“必填非空”和“必填可空”两种状态，不出现字段缺失。
- 禁止全局或 DTO 级使用 `@JsonInclude(JsonInclude.Include.NON_NULL)`。字段缺失和字段为 null 必须保持可区分。
- `List`、`Set`、`Map` 等集合字段默认返回空集合，不返回 null，并标注为必填非空。
- 计数、版本号、分页数字等业务上恒定的字段应返回确定值，不用 null 表达 0。
- 时间字段使用 `LocalDateTime` 或 `LocalDate`，禁止用 `Long` 表示时间戳。
- `R<Void>.data` 使用 `requiredMode = REQUIRED` 和 `nullable = true`。
- `PageR.list` 必填非空；`PageR.total/page/size/totalPage` 必填非空。
- `R.key` 和 `R.msg` 的最终目标是非空字符串。在 `R.ok()` 仍返回 null 的阶段，先按必填可空标注；完成 `R.ok()` 行为调整后再收紧为必填非空。

## 4. 规则三：枚举 wire 值统一为字符串

### 4.1 基本规则

- 所有对外暴露的枚举，JSON wire 值统一为 `String`，由 `@JsonValue` 标注。
- 数据库持久化可以使用数字 `@EnumValue`，但 API 契约只暴露字符串，数据库 code 不进入 OpenAPI。
- 禁止新增 `@JsonValue` 返回 `int`、`long` 的枚举。
- 请求体枚举通过 `@JsonCreator` 或统一 Jackson 配置解析 canonical wire value。
- `@RequestParam`、`@PathVariable` 中的枚举必须通过统一 `ConverterFactory` 解析 canonical wire value，禁止依赖 Spring 默认的枚举名绑定。
- OpenAPI 中的枚举值必须与 wire value 一致，不能展示 Java 枚举常量名。springdoc 未正确识别 `@JsonValue` 时，使用 `@Schema(allowableValues = {...})` 或在统一 OpenAPI customizer 中处理。
- body 和 query 两种入口都要有契约测试，至少覆盖一个 canonical 值和一个非法值。

### 4.2 存量枚举迁移

以下枚举当前 `@JsonValue` 返回数字，需要迁移为字符串：

| 枚举 | 当前 wire 值 | 目标 wire 值 |
| --- | --- | --- |
| `IdentityType` | `1/2/3` | `STUDENT/TEACHER/ADMIN` |
| `GroupType` | `1/2/3` | `NORMAL_GROUP/ADVANCED_GROUP/MARKET_GROUP` |
| `GroupRoleType` | `0/1/2/-1` | `OWNER/ADMIN/MEMBER/NOT_MEMBER` |

已经是字符串 wire 值的枚举保持不变。`MessageType.GROUP` 的 wire 值是 `"NORMAL"`，`"GROUP"` 不是合法 wire 值。

### 4.3 兼容窗口

- 迁移期间，统一转换器可以临时接受旧数字 code，避免已发布客户端立即失败；旧 code 不写入 OpenAPI，前端迁移完成后删除。
- 枚举常量名不自动作为兼容别名。只有与 wire value 相同，或明确列入兼容清单的值才可以接受。
- `ResourceType` 的 canonical wire 值是 `"PDF"`、`"DOCX"` 这类大写值；`"pdf"`、`"docx"` 等扩展名只作为上传创建场景的明确兼容别名，不扩散到所有枚举入口。

### 4.4 新枚举模板

```java
@Getter
@RequiredArgsConstructor
public enum ExampleType {

    FIRST(1, "FIRST"),
    SECOND(2, "SECOND");

    @EnumValue
    private final int code;

    @JsonValue
    private final String value;
}
```

## 5. 与既有序列化约定的关系

- Java `Long/long` 统一序列化为 JSON `string`，`Integer/int` 为 JSON `number`。
- `LocalDateTime` 统一序列化为毫秒时间戳 `number`，`LocalDate` 为 ISO 日期字符串。
- 这些行为由 `JacksonConfiguration` 统一控制，不要在单个 DTO 上另写序列化器改变 wire 类型。
- 字段的 OpenAPI 类型必须与上述 wire 类型一致；发现 schema 与运行时不一致时，优先修统一配置或 OpenAPI customizer，不要在前端加 union 类型。

## 6. 迁移顺序

1. 先落本文规则和审计脚本，冻结字段清单与违规基线。
2. 修复 P0 契约缺陷：Controller 返回 Entity、枚举 query 绑定、公开接口缺 `@Valid`、响应类型与实现不一致。
3. 按模块补响应字段的 required/nullable，优先 `common`、`user`、`resource`，再做 `document/note` 和 `ai-asset/file-storage`。
4. 用 OpenAPI snapshot 或契约测试锁定 wire 类型、required/nullable 和枚举值，禁止新增违规。

## 7. Review 检查清单

- 每个响应字段是否恰好使用一种 required/nullable 声明。
- 是否出现 `@JsonInclude(NON_NULL)`、`NOT_REQUIRED` 或响应 DTO 上的 `@NotNull` 代替契约声明。
- 集合字段是否默认空集合，且未返回 null。
- `R<Void>.data` 是否为必填可空，`PageR.list` 是否为必填非空。
- 枚举 `@JsonValue` 是否返回 `String`，query 和 body 是否使用同一套 wire value。
- OpenAPI 中的枚举值、required 列表和 nullable 是否与运行时一致。
