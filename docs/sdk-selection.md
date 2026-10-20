# AI SDK 选型记录

- 状态：已采纳
- 日期：2026-07-23
- 范围：Spring AI、DashScope Java SDK、LangChain4j、Spring AI Alibaba 四选一
- 结论：当前项目只使用官方 `dashscope-sdk-java`

## 1. 项目条件

这次选择不是抽象比较“哪个框架功能最多”，而是基于当前约束：

- Spring Boot 4.1、Java 21；
- 唯一模型和媒体供应商是阿里云百炼；
- 同时需要 Qwen 聊天、图片/视频理解、Function Calling、Qwen-Image 和 CosyVoice；
- Bot 自己需要掌控工具 allowlist、审批、轮次预算、会话串行和失败降级；
- 只允许引入一套 AI SDK；
- 目标是代码量适中、多人可按模块并行，而不是建设通用 Agent 框架。

## 2. 比较

| 方案 | 当前优势 | 当前代价 | 本项目结论 |
|---|---|---|---|
| Spring AI 2 | Spring Boot 4/4.1 对齐；Tool Callback、自动/手动工具循环和统一模型抽象成熟 | 百炼的图片、语音及部分原生多模态能力仍可能需要厂商适配；在“一套 SDK”限制下会迫使项目舍弃原生能力或自行补协议 | 若未来多 Provider/RAG 成为主需求，优先重新评估；当前不选 |
| DashScope Java SDK | 阿里云官方；最短路径覆盖当前 Qwen、多模态、图片和语音；厂商参数与错误信息完整 | 需要自己维护消息转换、工具循环、重试和可观测性 | **最符合当前单 Provider、全模态和强控制需求，已选** |
| LangChain4j | AI Services、Tool、Memory 和多模型抽象易用，纯 Java 体验好 | DashScope 集成已迁入 community 模块，并明确基于 DashScope Java SDK；等于在厂商 SDK 上再加一层，且本项目仍要保留自己的安全路由 | 能快速做通用原型，但对当前模块化单体增加重复抽象；不选 |
| Spring AI Alibaba | 对百炼集成深入，并提供 Graph、多 Agent、Nacos/MCP 等完整生态 | 当前需求并不需要 Agent Framework；2026-07 的 1.1.2.x 发布线与 Spring AI 1.1.2 对齐，而项目已是 Spring Boot 4.1；依赖面和升级联动更大 | 适合采用其完整 Agent/Graph 体系的新项目；当前不选 |

官方依据：

- Spring AI 2.0.x 官方说明支持 Spring Boot 4.0.x/4.1.x：
  [Spring AI Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html)。
- Spring AI 支持框架控制、Advisor 控制和用户控制的工具执行：
  [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)。
- DashScope 官方仓库给出 `com.alibaba:dashscope-sdk-java` 依赖和原生 Generation API：
  [DashScope Java SDK](https://github.com/dashscope/dashscope-sdk-java)。
- LangChain4j 文档说明 DashScope 集成使用 DashScope Java SDK，并从 1.0.0-alpha1 起迁入
  `langchain4j-community`：
  [LangChain4j DashScope integration](https://docs.langchain4j.dev/integrations/language-models/dashscope/)。
- LangChain4j 同时提供低层 `ChatModel/ToolSpecification` 和高层 AI Services：
  [LangChain4j Tools](https://docs.langchain4j.dev/tutorials/tools/)。
- Spring AI Alibaba 的 v1.1.2.0 发布说明明确升级并对齐 Spring AI 1.1.2：
  [Spring AI Alibaba releases](https://github.com/alibaba/spring-ai-alibaba/releases)。

## 3. 为什么“整体最方便”不是 Spring AI

只看纯文本聊天和自动工具调用，Spring AI 的开发代码最少。但本项目的完整链路还包括图片/视频输入、
Qwen-Image 和 CosyVoice。如果为聊天引入 Spring AI，又为这些能力保留 DashScope SDK，就违背了“一套 SDK”
约束，并形成两套消息、异常、配置和重试模型。

选择 DashScope 后，额外代码集中在两个位置：

- `infrastructure/ai/DashScopeModelClient`：厂商协议适配；
- `agent/AgentToolLoop`：项目自己的安全工具循环。

其他模块完全不知道 DashScope。手写的是稳定且项目确实需要的控制面，不是散落在业务代码里的 HTTP 协议。

## 4. 落地约束

POM 只允许一项 AI SDK：

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>dashscope-sdk-java</artifactId>
</dependency>
```

以下规则由测试保护：

- 主代码不得导入 `org.springframework.ai` 或 `dev.langchain4j`；
- POM 不得出现 `spring-ai`、`langchain4j` 或 `spring-ai-alibaba`；
- `com.alibaba.dashscope` 只能出现在 `infrastructure`；
- `agent` 只依赖 `AgentModelClient`；
- Function Calling 循环和 Tool 注册表不得使用厂商类型。

## 5. 重新评审触发条件

出现以下任一情况时，应新增一份选型记录，而不是直接在业务代码混入第二套框架：

- 正式支持两个及以上模型 Provider；
- RAG、向量库或统一可观测能力成为核心需求；
- DashScope SDK 无法支持必须使用的模型能力；
- Spring AI Alibaba 发布线与当前 Spring Boot/Spring AI 主线稳定对齐，且项目决定采用其 Agent Graph；
- 自维护工具循环的实际复杂度明显超过框架迁移成本。

重新评审时仍应保持 `AgentModelClient` 和 `AgentTool` 合同稳定，先在 `infrastructure` 做可替换适配器，再决定
是否替换，而不是让框架类型扩散到 Channel、Capability 或 Tool。
