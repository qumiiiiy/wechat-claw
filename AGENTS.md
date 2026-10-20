# WeChat Claw 项目协作规范

本文件适用于整个仓库。它描述当前模块化单体的开发边界；用户在具体任务中的明确要求优先。

## 1. 产品和架构原则

WeChat Claw 是自托管的模块化单体微信智能体，不是微服务系统。

- 不拆微服务，不引入 API 网关或分布式中间件来解决进程内问题。
- 不为每个业务类机械创建 `Service/ServiceImpl`、Manager、DTO 和转换器。
- 按“变化原因”放置代码，让渠道、Agent、能力、工具、基础设施和存储可以分别演进。
- 对外部系统建立窄适配边界；业务代码依赖稳定模型，不依赖 SDK 对象。
- 新功能优先复用现有模块合同；只有合同真实变化时才跨模块修改。
- README 只描述已经可运行的能力。计划中的多渠道、Skill、调度、多 Agent 等不得写成已实现。

## 2. 固定模块

Java 根包为 `com.weida.bot.wechatilinkbot`，只使用以下七个职责模块：

```text
com.weida.bot.wechatilinkbot
├── channel
│   ├── ilink
│   └── web
├── agent
│   └── intent
├── capability
├── tool
│   └── input
├── infrastructure
│   ├── ai
│   ├── concurrency
│   ├── mail
│   ├── media
│   ├── ratelimit
│   ├── serialization
│   └── weather
├── persistence
│   └── mybatis
└── model
```

不要恢复 `controller`、`service`、`service.impl`、`manager`、`dao`、`dto`、`vo`、`common`、
`helper`、`util` 等旧式或含义模糊的顶层业务包。Web 接口属于 Channel，Mapper 属于 Persistence，
跨模块稳定数据属于 Model。

### channel

负责微信登录、轮询、入站消息规范化、出站消息、媒体下载、SDK 类型转换和渠道 HTTP 接口。

- `wechat-ilink-sdk` 的所有类型只允许出现在 `channel/ilink`。
- 不修改、复制、反编译后重写或影子实现 SDK 源码。
- `IlinkSessionRegistry` 管理会话集合；单会话状态机放在 `IlinkBotSession`。
- 消息解析、Agent 调用和回复发送保持为独立组件。
- 微信 iLink Bot 不具备任意主动发送能力。文字、图片、文件、语音和视频只能作为一条真实入站消息的回复，
  并使用该入站消息携带的有效消息 Token；只有目标用户 ID 或已登录 Client 不足以授权发送。
- 消息 Token 只能停留在 `channel/ilink`，必须绑定产生它的 Bot 会话、入站消息和目标用户，不得由模型传入，
  不得写入日志、数据库或 Agent 历史，也不得跨入站消息复用。
- `IlinkMessageHandler` 必须在本轮处理期间保存可信回复上下文，`IlinkReplySender` 和所有 SDK 发送入口必须显式
  要求该上下文。后台任务、Cron、Capability、Tool 和管理接口不得绕过回复上下文直接调用发送方法。
- 定时任务只能计算并保存“待回复内容”。用户下一次发送消息并产生新 Token 后，Channel 才能在该回复窗口内
  决定是否附带待回复内容；没有有效 Token 时保持待发送状态，不得伪造成功。
- Channel 不实现模型协议、工具循环或外部业务 Provider。

### agent

负责任务编排、上下文、意图、模型端口、模型选择、Function Calling 循环和失败降级。

- Agent 只依赖 `AgentModelClient`，不得导入任何模型厂商 SDK。
- 同一会话的 Agent Run 必须串行，不同会话可在有界并发内并行。
- 模型输出不代表动作成功；最终状态必须由工具执行结果决定。
- 轮次、历史长度、字符数、媒体数和并发必须有上限。
- 具体工具通过注册表发现，不在 Runtime 中硬编码工具实例。

### capability

负责用户可感知的一项完整能力，例如天气、文档、图片/语音、文本文件或邮件。

- Capability 包含该能力的业务校验、权限、审批、配额和降级语义。
- 外部协议和客户端细节留在 Infrastructure。
- 一个能力只有一个实现时直接使用明确命名的组件，不创建无意义接口。
- 只有存在真实替换边界或多个实现时才引入接口，并在 Javadoc 说明边界。

### tool

负责 Function Calling 的稳定协议、Schema、统一注册和工具实现。

- 工具实现 `AgentTool<I>`；输入使用不可变 record，并用 `@ToolParameter` 描述字段。
- Tool 只能依赖 Capability 和 Model，不得依赖 Agent、Channel、Infrastructure 或 Persistence。
- Tool 名称稳定且唯一；变更已发布名称需要兼容方案。
- Tool 必须声明可用性；高风险或付费工具默认不可用。
- 参数、返回值和异常均视为不可信，不得把模型参数直接传给文件系统、SMTP 主机或命令行。

### infrastructure

负责模型、天气、媒体、SMTP、FFmpeg、HTTP、限流、重试、监控、线程池和序列化等技术适配。

- `com.alibaba.dashscope.*` 只能出现在本模块。
- Provider 适配器实现 Agent/Tool 定义的端口，但不得包含业务编排。
- 网络请求必须配置连接/请求超时、响应大小和有限重试；有副作用请求默认不自动重试。
- 线程池必须命名、有界并配置拒绝策略；禁止 `new Thread` 和无界 `Executors` 工厂。
- 外部错误转换为稳定异常，不把密钥、完整上游响应或临时签名 URL 泄露到其他模块。

### persistence

负责会话、消息、用户偏好、行动记忆和工具记录的存储。

- `ConversationMemory` 是会话记忆替换边界。
- MyBatis Java 接口放在 `persistence/mybatis`，SQL 只放在
  `src/main/resources/mapper/*Mapper.xml`。
- Java 中禁止 MyBatis SQL 注解、SQL 字符串和手写 JDBC Mapper。
- SQL 显式列出字段，禁止 `SELECT *`，用户输入只使用参数绑定。
- Schema、模型、Mapper XML、持久化测试和 `docs/sqlite-schema.sql` 必须同步。
- SDK Client、Token、API Key、授权码和临时媒体 URL 不得持久化。

### model

只放跨两个及以上模块使用、语义稳定的业务数据结构。

- Model 不依赖其他业务模块、Spring、SDK、数据库或网络。
- 优先使用不可变 record；含 `byte[]` 等可变成员时防御性复制。
- 不把临时 Web 请求、SDK DTO 或数据库专用字段塞进 Model。
- Model 不是独立业务层，不允许放流程和外部调用。

## 3. 依赖边界

允许的主要依赖：

```text
channel        -> agent, capability, infrastructure, model
agent          -> capability, tool, persistence, model
tool           -> capability, model
capability     -> infrastructure, model
infrastructure -> agent/tool 的端口与协议, model
persistence    -> model
model          -> 无业务模块
```

禁止用延迟注入、静态容器或运行时查 Bean 掩盖循环依赖。发生循环时应重新分配职责。

边界变更必须同时修改 `ArchitectureRuleTest` 和 `docs/architecture.md`，并说明为什么原方向不足。
不要为了让测试通过而放宽整个模块，只为有明确语义的端口增加最小例外。

## 4. AI SDK 和 Provider

当前只允许官方 `com.alibaba:dashscope-sdk-java` 一套 AI SDK。

- 不引入 Spring AI、Spring AI Alibaba、LangChain4j 或第二套模型 SDK。
- DashScope Chat、图片、语音及多模态调用集中在 Infrastructure。
- Agent 自己维护厂商无关的 Function Calling 循环。
- 新 Provider 必须先实现 `AgentModelClient`，不得让其类型扩散到 Agent、Tool、Capability 或 Channel。
- 若需求确实需要多 Provider/RAG/Agent Graph，先更新 `docs/sdk-selection.md` 形成评审结论，再改 POM。

## 5. 新功能放置指南

开始编码前先判断变化点：

1. 微信载荷、登录或发送行为变化：改 `channel/ilink`。
2. 上下文、路由、工具选择或降级变化：改 `agent`。
3. 用户获得一种完整新行为：在 `capability` 实现。
4. 希望模型选择该能力：再增加一个 `AgentTool` 和输入 record。
5. 需要第三方 API、进程或协议：在 `infrastructure` 增加适配器。
6. 需要跨重启保存：实现或扩展 `persistence`。
7. 只有多个模块都需要且语义稳定时，才扩展 `model`。

典型的新工具不应要求修改 Channel、Agent Runtime 分支或 Persistence。若确实需要，先说明额外耦合的原因。

## 6. Java 和命名

- 使用 Java 21、UTF-8、LF、4 空格缩进和 K&R 大括号。
- 包名小写；类名 UpperCamelCase；方法和变量 lowerCamelCase；常量大写下划线。
- 类名缩写按单词处理：`Ilink`、`Http`、`Qr`、`Id`，保持仓库现有命名一致。
- 即使只有一行，`if/for/while` 也必须使用大括号。
- 单行不超过 120 字符；方法原则上不超过 80 行。
- 优先卫语句和小型协作类；不要把新分支继续堆入 300 行以上的热点类。
- 所有覆写方法加 `@Override`；访问范围默认最小。
- 时间使用 `java.time`；精确小数使用 `BigDecimal`；可空数值使用包装类型。
- 正则预编译为 `Pattern`；资源使用 try-with-resources。
- 工具类使用私有构造方法；不要新增含义模糊的 `Utils`。
- 捕获 `InterruptedException` 后恢复中断标志。
- 不保留注释掉的大段代码。TODO 必须包含负责人、日期和计划完成时间。

## 7. 配置

配置前缀按所有者划分：

```text
bot.channel.*
bot.agent.*
bot.capability.*
bot.infrastructure.*
bot.persistence.*
```

- 配置类只做绑定、默认值和结构校验，不承载业务流程。
- 密钥、Token、密码和授权码只从环境变量或密钥管理注入，默认值必须为空。
- 新配置必须有安全默认值、明确上限，并同步 `application.properties` 与 README。
- 已公开环境变量尽量兼容；废弃时记录迁移方式，不静默改变含义。

## 8. 安全

- 默认拒绝：新 Tool、主动发送、文件生成和付费媒体能力默认关闭或受显式配置控制。
- 身份优先：高风险能力使用可信 Channel 提供的会话身份，不信任模型传入的身份。
- 微信发送必须同时具备可信 Channel 身份和当前入站消息 Token；缺失、过期、会话错配或目标错配时默认拒绝。
- 所有外部输入、模型输出和工具结果均不可信，执行前校验类型、长度、枚举、Schema 和权限。
- 外部下载只允许 HTTPS 与主机 allowlist；每次重定向后重新校验，限制大小、超时和并发。
- 文件名规范化后确认没有路径；模型不能指定服务器路径或可执行文件。
- 系统命令参数不得经 Shell 拼接；限制可执行文件、参数、工作目录、超时和输出。
- SQL 只参数绑定；Web 输出和状态变更使用相应转义、鉴权和 CSRF/等效保护。
- 邮件等高风险动作必须审批、allowlist、频率限制和审计，且不自动重试不确定的副作用。
- 日志不得包含密钥、Token、密码、授权码、完整个人敏感数据、邮件正文或临时签名 URL。

## 9. 异常、日志和降级

- 不吞异常，不用异常控制正常流程，不直接抛无语义的 `Exception`。
- 外部适配器保留原异常为 cause，并向上提供稳定、不含敏感信息的错误。
- 同一失败只在拥有完整现场的边界记录一次；使用 SLF4J `{}` 占位符。
- error 日志包含关联字段和堆栈；用户输入问题通常使用 warn 或稳定 4xx。
- 模型或工具失败时必须明确失败，不能根据模型文字伪造“已发送”“已生成”或“已查询”。
- 降级回复应可测试，不依赖人工观察日志。

## 10. 测试

测试使用 JUnit 5、AssertJ 和 Mockito，遵守 Automatic、Independent、Repeatable。

- 每个行为变更至少覆盖成功和失败；缺陷修复必须有回归测试。
- Agent 工具循环覆盖无工具、单工具、多轮、未知工具、禁用工具、异常和轮次上限。
- 会话协调覆盖同会话串行和跨会话并行。
- Capability 覆盖权限、边界、审批、限流和外部失败。
- Channel 适配测试可以使用 SDK 类型，但不调用真实网络。
- Infrastructure 用可替换客户端、Mock 或本地假实现，单元测试不依赖真实外部服务。
- Persistence 覆盖内存和 Mapper 映射；修改 Schema 时同步测试。
- 架构测试必须继续保护七模块、SDK 边界、单 AI SDK 和依赖方向。

修改后至少运行：

```powershell
.\mvnw.cmd test
```

交付前运行：

```powershell
.\mvnw.cmd clean verify
```

不得通过跳过测试、降低架构断言或吞掉构建警告来完成任务。

## 11. 文档和提交前检查

架构变化同步更新 `docs/architecture.md`；AI SDK 变化同步更新 `docs/sdk-selection.md`；运行方式、配置或用户行为
变化同步更新 README。数据库变化同步更新 `docs/sqlite-schema.sql`。

提交前确认：

1. 改动是否落在正确模块，是否引入循环或旧分层包。
2. 微信和 DashScope SDK 类型是否仍停留在各自适配边界。
3. 是否机械增加接口/实现、DTO 或转换层。
4. 新能力是否具备默认拒绝、输入上限、权限、超时和失败语义。
5. 密钥和敏感内容是否可能进入配置、日志、数据库或测试快照。
6. 并发、队列、网络响应、媒体和历史是否全部有界。
7. 测试和文档是否与行为同步。
8. `.\mvnw.cmd clean verify` 是否通过。
