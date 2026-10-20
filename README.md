# WeChat Claw

WeChat Claw 是一个基于 Spring Boot 4.1 和 Java 21 的模块化单体微信智能体。
微信 iLink 负责登录与消息通道，Bot 自己负责 Agent 编排、Function Calling、用户能力、基础设施和记忆。
项目不拆微服务，不引入 API 网关，也不为每个类机械创建 `Service/ServiceImpl`。

当前实现包括：

- 多个微信 Bot 独立扫码、轮询、收发消息和状态管理；
- 同一会话严格串行、不同会话受控并行的 Agent Runtime；
- Qwen 文本及图片/视频理解，以及文本翻译；
- 天气查询、图片生成、二维码生成、语音生成、Word/PDF/XLSX/PPTX 文件、受限文本文件和两阶段确认邮件工具；
- PlantUML 图表生成，以及进程内情感画像记忆；
- 文档正文提取、引用消息、多媒体下载与长回复转文件；
- 进程内或 SQLite 会话记忆，以及最近生成图片和文件的短期记忆；
- 会话级短期自然引用和统一手机输出，文件、待办、课程、Desktop/OCS 状态不要求用户复制内部编号；
- 入站限流、入站消息去重与有界重试、有界线程池、结构化日志和本地降级回复。

架构和协作边界见 [架构说明](docs/architecture.md)，AI SDK 选型依据见
[SDK 选型记录](docs/sdk-selection.md)，开发约束见 [AGENTS.md](AGENTS.md)。

## 为什么只使用 DashScope Java SDK

本项目只接入阿里云百炼，并且同时需要聊天、多模态、Function Calling、图片和语音。为了避免“聊天一套框架、
媒体再补一套厂商 SDK”，AI 侧只保留官方 `dashscope-sdk-java`。Agent 自己维护一个小型、可测试、厂商无关的
工具循环；DashScope 类型只允许出现在 `infrastructure`。

这不表示 DashScope 在所有 Java AI 项目里都优于 Spring AI。若以后真正需要多 Provider、RAG 或统一的
Spring AI 生态能力，可在不改 Channel、Capability 和 Tool 的前提下新增 `AgentModelClient` 适配器，再重新
评审 SDK。当前的详细比较见 [SDK 选型记录](docs/sdk-selection.md)。

## 代码结构

```text
src/main/java/com/weida/bot/wechatilinkbot/
├── channel/         微信登录、入站规范化、出站投递、媒体下载和 HTTP 管理接口
│   ├── ilink/       wechat-ilink-sdk 的唯一适配边界
│   └── web/         登录页和 Bot 管理/发送接口
├── agent/           会话协调、意图解析、上下文组装、模型选择和 Function Calling 循环
├── capability/      天气、图片/语音、文档、文本文件和邮件等完整用户能力
├── tool/            厂商无关的 Tool SPI、JSON Schema、注册表和工具实现
├── infrastructure/  DashScope、和风天气、QQ SMTP、FFmpeg、限流、线程和序列化适配
├── persistence/     内存/SQLite 会话记忆、最近图片记忆和 MyBatis 映射接口
├── model/           跨模块稳定数据结构
└── WechatIlinkBotApplication.java
```

两个外部 SDK 都有硬边界：

- `com.github.wechat.ilink.*` 只能出现在 `channel/ilink`；
- `com.alibaba.dashscope.*` 只能出现在 `infrastructure`。

`wechat-ilink-sdk` 通过 Maven/JitPack 依赖使用。仓库不会复制、修改或重写它的源码。

## 本地运行

要求：

- JDK 21；
- 可用的阿里云百炼 API Key；
- 图片/语音输出需要 `ffmpeg`，建议同时安装 `ffprobe`；
- 除邮件外，能力主开关默认开启；天气、学习通、DashScope、FFmpeg 和 PlantUML 等外部依赖仍需各自配置，
  未满足依赖的工具会在启动自检中标记为不可用；会话记忆默认使用 SQLite。

PowerShell 最小配置：

```powershell
$env:DASHSCOPE_API_KEY = 'sk-替换为真实Key'
.\mvnw.cmd spring-boot:run
```

启动完成后会自动打开 [http://localhost:8080/wechat/login](http://localhost:8080/wechat/login)，在页面创建 Bot 并扫码。
无桌面环境时不会自动打开浏览器，可手动访问该地址；也可以设置 `WECHAT_AUTO_OPEN_LOGIN_PAGE=false` 关闭自动打开。
默认只监听 `127.0.0.1`。

常用可选配置：

```powershell
# 和风天气
$env:QWEATHER_API_HOST = 'https://你的专属API-Host'
$env:QWEATHER_API_KEY = '替换为真实Key'

# SQLite 会话记忆（默认文件为 ./data/wechat-bot.db）
$env:BOT_MEMORY_TYPE = 'sqlite'
$env:SQLITE_JDBC_URL = 'jdbc:sqlite:./data/wechat-bot.db'
$env:SQLITE_BUSY_TIMEOUT_MILLIS = '5000'

# 入站消息可靠性：SQLite 账本按 Bot + message_id 去重；不保存 context token 或消息正文
$env:WECHAT_MESSAGE_RETRY_MAX_ATTEMPTS = '5'
$env:WECHAT_MESSAGE_RETRY_INITIAL_DELAY = '2s'
$env:WECHAT_MESSAGE_RETRY_MAX_DELAY = '5m'
$env:WECHAT_MESSAGE_PROCESSING_LEASE = '5m'
$env:WECHAT_MESSAGE_DEDUPLICATION_TTL = '3d'
$env:WECHAT_AUTO_OPEN_LOGIN_PAGE = 'true'

# 上下文压缩：先无损合并旧消息，仍超限时再生成有损摘要
$env:BOT_MAX_HISTORY_CHARACTERS = '40000'
$env:BOT_CONTEXT_SUMMARY_MAX_CHARACTERS = '4000'
$env:BOT_CONTEXT_COMPRESSION_KEEP_MESSAGES = '6'

# 邮件能力（支持最近生成文件作为附件；仍需凭据、会话和收件人白名单）
$env:BOT_EMAIL_ENABLED = 'true'  # 邮件默认关闭，需显式开启
$env:QQ_EMAIL_ADDRESS = '你的QQ邮箱@qq.com'
$env:QQ_SMTP_IMAP = 'QQ邮箱SMTP授权码'
$env:BOT_EMAIL_ALLOWED_CONVERSATIONS = 'wechat-ilink|bot=<BotID>|user=<微信用户ID>'
$env:BOT_EMAIL_ALLOWED_RECIPIENTS = 'friend@example.com'
# 可选：草稿确认有效期，默认 30 分钟，最大 1 小时
$env:BOT_EMAIL_APPROVAL_EXPIRE_AFTER = '30m'

# Excel 工作簿生成
$env:BOT_SPREADSHEET_ENABLED = 'true'
# 可选：服务端受控输出目录，默认 generated/spreadsheet
$env:BOT_SPREADSHEET_OUTPUT_DIRECTORY = 'generated/spreadsheet'

# PPTX 演示文稿生成
$env:BOT_PPTX_ENABLED = 'true'
$env:BOT_PPTX_OUTPUT_DIRECTORY = 'generated/pptx'

# PlantUML 图表渲染工具（需要外部 CLI）
$env:PLANTUML_ENABLED = 'true'
# PLANTUML_EXECUTABLE 默认是 PATH 中的 "plantuml.bat"；自定义路径可以填写启动器文件，
# 也可以填写包含 plantuml.bat（Windows）或 plantuml（Unix）的目录。
```

完整默认值和环境变量映射位于
[`application.properties`](src/main/resources/application.properties)。密钥默认值均为空，不应写入仓库。

### 本机文件搜索

文件搜索默认开启，`BOT_FILE_BROWSE_ALLOWED_PATHS=*` 表示搜索当前电脑所有已挂载的文件系统根目录；也可以改成
逗号分隔的目录列表来收窄范围。搜索始终受最大深度、条目数、结果数、超时和文件大小限制，并跳过符号链接、敏感
目录/文件以及脚本（bat/cmd/ps1/sh 等）和密钥证书（key/pem/pfx/p12 等）扩展名；默认允许常见文档、压缩包、
图片视频以及 exe/msi/apk，可通过 `BOT_FILE_BROWSE_ALLOWED_EXTENSIONS` 调整。结果不会向手机显示绝对路径。
自然语言理解由模型完成：
模型把用户描述转换成结构化搜索条件（文件名关键词、类型、目录、排序、是否只列目录、是否指代最近收到的文件），
服务端只做有界校验和执行；文件名关键词允许同义/相关词（例如“课表”和“课程表”），任一命中即返回并按命中数排序。
“最近的 N 个文件”使用 `recent_first` 排序并按修改时间取前 N 个，不限定 30 天窗口；整盘扫描耗时较长，
默认条目/时限预算为 50 万条、60 秒，可通过 `BOT_FILE_BROWSE_MAX_ENTRIES` 和 `BOT_FILE_BROWSE_MAX_SCAN_DURATION`
调整。

### 二维码和情感画像

`generate_qr_code` 使用 ZXing 生成 PNG，不需要额外密钥。情感画像默认保存在进程内，并从模型回复末尾的内部标记
提取后注入后续上下文；标记包含结构化情绪等级 `[MOOD: low|neutral|positive]`，主动关怀只依据该等级判定，
不再用关键词猜测情绪。标记不会发送给用户。主动关怀消息主开关默认开启，仍只会保存待回复内容，等待下一条真实
微信消息窗口发送。

### Excel 工作簿工具

设置 `BOT_SPREADSHEET_ENABLED=true` 后，模型会在服务端筛选出的可用工具目录中判断是否生成 Excel 或 XLSX
工作簿。模型只能提供标题、工作表名、列名和文本数据行，不能指定服务器文件名或路径；生成文件固定写入受控目录，
重新通过 Apache POI 打开验证后才交给微信文件通道。

最近生成或通过文件搜索安全选择的 `.xlsx` 会作为当前会话的短期可信文件。用户可以继续要求读取其工作表摘要，
或增加一个结构化工作表；追加操作只产生受控副本，不修改原文件。读取和追加工具都不接受模型提供的路径。

V1 只生成文本单元格，默认最多 64 列、10,000 行、单元格 32,767 字符和 10 MiB 输出。若同一条消息明确要求
生成 Excel 并作为邮件附件发送，Agent 会先生成 XLSX，再准备含附件的邮件草稿；邮件仍需用户下一条消息单独确认。

### PPTX 演示文稿工具

默认开启时，用户可以直接描述主题和提纲生成 PPTX。模型只提交受限的标题页、要点页、
表格页或结束页结构，输出目录、文件名、页数、文字长度和文件大小由服务端配置；生成后先回读校验，再作为微信
文件发送。默认不接受模型提供的本机图片路径，图文页需要后续接入已批准媒体。

### PlantUML 图表工具

`generate_diagram` 默认开启，但依赖外部 PlantUML CLI：

- 服务器需先安装 PlantUML（参见 [plantuml.com](https://plantuml.com/)），把 `plantuml.bat` 放入 `PATH`，
  或设置 `PLANTUML_EXECUTABLE` 指向启动器文件的绝对路径；也可直接填写包含 `plantuml.bat`
  （Windows）或 `plantuml`（Unix）的目录，程序会自动选择目录内的启动器。
- `PLANTUML_ENABLED` 保持开启时，`generate_diagram` 才会在 `ToolRegistry` 中可用；可执行文件被探测一次，
  缺失则工具返回 `executable_missing`，不会让 Bot 报错。
- 模型只能指定类型（如 `sequence` / `class` / `flowchart` 等）和 PlantUML 源码；可执行文件、临时目录、
  超时、输出字节上限均来自配置，模型无权修改。
- 任何包含 `!` 前处理指令（`!include` / `!includeurl` / `!import` / `!pragma` 等）或精灵包含 `{{...}}`
  的源码都会被拒绝，避免路径穿越或远端拉取；缺 `@startuml` / `@enduml` 同样会被拒绝。
- 类型白名单与字符上限（默认 50 000 字符、7 MiB 输出）来自配置，可在 `application.properties` 中调整。

#### Windows `plantuml.bat` 启动器的生成和内容

PlantUML 本体是 JAR 文件，`plantuml.bat` 不是 Claw、Spring Boot 或 Maven 自动生成的业务文件，而是 Windows
命令行启动器。当前部署中的脚本逻辑只有两步：关闭命令回显，然后用 Java 启动 PlantUML JAR，并用 `%*` 原样转发
Claw 传入的全部参数。Claw 当前会传入类似 `-tpng -charset UTF-8 -failfast2 <source-file>` 的参数，因此脚本不应
自行解析、删除或重排参数。

当前脚本是“固定 JAR 绝对路径”的最小写法（下方用占位符隐藏本机目录；双引号数量按当前文件原样保留）：

```bat
@echo off
java -jar ""<PlantUML目录>\plantuml-SNAPSHOT.jar"" %*
```

它不包含 PlantUML 算法，也不下载文件；真正的渲染逻辑在 JAR 内。`@echo off` 只是不把命令本身打印到终端，`java
-jar` 启动 JAR，`%*` 把所有调用参数透传给 PlantUML。当前 Windows 环境可以容忍这组重复引号，但推荐下面的模板
使用一对标准引号、相对脚本目录定位 JAR。Java 进程的退出码也会作为脚本退出结果，Claw 据此判断启动或渲染是否成功。

推荐用下面的 PowerShell 片段重新生成脚本。它把 JAR 放在 `.bat` 同目录，用 `%~dp0` 取得脚本所在目录，因此移动
整个 PlantUML 目录后仍可用；同时检查 JAR 是否存在，并保留 Java 的退出码：

```powershell
$plantumlDir = 'D:\tools\plantuml'
$plantumlBat = Join-Path $plantumlDir 'plantuml.bat'

@'
@echo off
setlocal
set "PLANTUML_JAR=%~dp0plantuml-SNAPSHOT.jar"
if not exist "%PLANTUML_JAR%" (
    echo PlantUML JAR not found: "%PLANTUML_JAR%" 1>&2
    exit /b 1
)
java -jar "%PLANTUML_JAR%" %*
exit /b %ERRORLEVEL%
'@ | Set-Content -LiteralPath $plantumlBat -Encoding ascii
```

如果 JAR 文件名不是 `plantuml-SNAPSHOT.jar`，只需同步修改 `PLANTUML_JAR` 那一行。生成后可用
`plantuml.bat -version` 验证；在 Claw 中可将包含该脚本的目录加入 Windows `Path`，并让
`PLANTUML_EXECUTABLE=plantuml.bat` 使用 PATH 查找，或者直接把 `PLANTUML_EXECUTABLE` 设为 `.bat` 文件的完整
路径。修改系统环境变量后必须重启终端、IDE 或 Bot 进程，旧进程不会自动获得新环境变量。

**GPL 合规**：本工具仅以独立 CLI 进程方式调用 PlantUML，未在仓库内静态链接或再分发 PlantUML 类库；
PlantUML 本身的 GPL 许可由运维侧在服务器上独立安装并承担。

## 消息与能力行为

### 入站消息

Channel 保留微信消息项的顺序，将 SDK 类型转换成稳定的 `AgentRequest`。文字直接进入上下文；图片和视频以
受限字节载荷进入多模态模型；语音使用微信转写；文件经 Apache Tika 限量提取正文。引用消息最多解析三层，
缺失或不可读取的内容会明确标记，不会假装读取成功。

单个文件默认不超过 10 MiB，最多提取 30,000 个字符。扫描版 PDF 暂未接入 OCR。

### Agent 与工具

`AgentRuntime` 读取并裁剪历史、提供受服务端可用性筛选的工具目录，然后由 `AgentToolLoop` 在配置的轮次预算内执行：

1. 向模型发送消息和可用工具定义，由模型判断是否调用工具、选择哪个工具以及调用顺序；
2. 校验模型返回的工具名与 JSON 参数；
3. 通过 `ToolRegistry` 执行工具；
4. 将工具结果（包括生成图片）作为 `tool` 消息交还模型；
5. 得到最终文本，或在达到预算、超时、工具失败时降级。

依赖型多步动作由模型在拿到前一步工具结果后继续决定下一步；同一会话的服务端仍严格串行、限制轮次和执行时间。
邮件确认、电脑操作、文件引用和微信发送等安全边界仍由服务端强制校验，模型不能用普通文本跳过审批或伪造成功。
自然文件选择发邮件时，服务端先复验并准备附件，不会把附件额外发送到微信。

工具结果先经过统一手机渲染。用户可以说“把第一个发给我”“取消刚才那个提醒”“进度怎么样”“开始第一门课”，
服务端只在当前微信会话、对象类型和短时 TTL 内解析自然引用；过期或会话不匹配会安全拒绝。内部 file ID、待办 ID、
课程引用、Desktop/OCS 会话号、JSON、工具名和本机绝对路径不会进入微信正文。应用启动时还会自检文件目录、QQ
SMTP 发信、学习通、OCS、题库和 Office 能力；缺配置时手机回复具体缺项和当前可用能力。只读 IMAP 待办来源尚未接入。

当前工具为 `get_current_weather`、`translate_text`、`generate_image`、
`generate_speech_video`、`generate_qr_code`、`generate_document`、
`generate_text_file`、`generate_spreadsheet`、`generate_presentation`、`read_spreadsheet`、`append_spreadsheet`、
`generate_diagram`、`send_email`、受控文件搜索/选择、学生待办、学习通查询和本机网址打开工具。
工具结果与模型文字都不被视为可信输入。其中 `generate_text_file`、`generate_spreadsheet`、
上述能力的主开关默认开启；语音工具仍要求本机可用 FFmpeg，邮件、学习通、天气、模型和 PlantUML 仍取决于对应的
授权、API Key、外部服务和本机依赖。

### 学习通手机扫码登录

启用学习通能力后，在微信中发送“登录学习通”，Bot 会从学习通官方登录页取得一个短时二维码，并作为微信图片
回复。使用手机上的学习通 App 扫码并确认后，直接发送“查课程”；课程查询与扫码轮询复用同一个仅存于进程内的
HTTP 会话，不需要访问电脑、不需要公网地址，也不会把二维码或 Cookie 写入 `Pictures/Screenshots` 或其他
磁盘目录。应用重启、二维码过期、在 App 中取消或切换账号后需要重新获取二维码。

```properties
CHAOXING_ENABLED=true
CHAOXING_LOGIN_CHALLENGE_TIMEOUT=3m
```

`CHAOXING_LOGIN_CHALLENGE_TIMEOUT` 只能设置为 30 秒至 5 分钟。原有 `CHAOXING_COOKIE` 仅作为兼容配置保留；
日常登录无需复制 Cookie。若部署重启后仍需复用已有会话，只能由部署环境在启动时注入 `CHAOXING_COOKIE`（例如
Windows 服务或密钥管理器的环境变量）；程序不会把扫码得到的 Cookie 写入 SQLite、普通文件、日志或插件。
未配置外部 Cookie 时，应用重启后需要重新扫码。此登录会话用于服务端只读课程查询，与下面 OCS 默认浏览器中的独立登录状态互不共享。
课程列表会在服务端为每门课程保存本次查询中的短期序号。随后可以发送“查第一门课的任务”、
“查高等数学的详情”或“查所有未完成任务”；手机只显示课程名和序号，不要求复制内部引用。课程、单门任务和
跨课程未完成任务均使用课程列表返回的课程专属 `cpi`，任务列表请求还会使用课程详情页的作业加密标识
（`workEnc`），不会用登录账号 `uid` 代替。课程会全部参与查询和汇总，任务输出有数量上限；登录失效、
课程缺少查询标识、页面变化或上游无权限会明确报错，不会伪装成空列表。
学习通未完成任务会按截止时间合并进统一待办视图；`query_todos` 支持按来源和自定义时间范围过滤，
例如 `filter=COURSE_ONLY` 配合 `from=2026-07-01` 只保留 7 月及以后的学习通任务。
没有明确时间范围时，`list_todos` / `query_todos` 默认只返回一个月内（含逾期）的任务；
明确说“全部待办”时才返回全部。

### 本机电脑任务（主开关默认开启）

当 Bot 运行在需要控制的 Windows 电脑上时，可启用受控的本机网址打开能力。它不是远程桌面、
Shell 或 GUI 自动化：V1 允许已登录 Bot 收到的真实微信会话请求打开任意格式合法且未嵌入账号信息的 HTTPS
网址。Bot 只创建待确认任务；创建任务的同一会话必须在下一条消息中单独回复任务 ID，才会调用本机默认浏览器。
任务状态和真实结果保存在 SQLite，失败不会伪装为已完成。

```properties
BOT_DESKTOP_ENABLED=true
BOT_DESKTOP_APPROVAL_EXPIRE_AFTER=5m
```

Spring Boot 默认以 AWT 无头模式启动。启用该能力时还必须给 Java 进程添加 VM 参数
`-Djava.awt.headless=false`；IntelliJ 可将其填入运行配置的 “VM options”。未使用交互式桌面会话或未添加该
参数时，桌面工具会报告不可用，不会创建一个注定无法执行的任务。

启用前请确认 Windows 保持登录、Bot 进程正在运行，并且该用户身份确实属于电脑所有者。不要以管理员账户
运行 Bot，也不要把任务数据库或本机管理接口暴露到公网。明确要求“打开 QQ 邮箱”时，Bot 使用
固定的 `https://mail.qq.com/`；其他网站仍必须由用户提供完整的 HTTPS 网址。
已有部署若希望额外收紧范围，仍可选配 `BOT_DESKTOP_ALLOWED_CONVERSATIONS` 和
`BOT_DESKTOP_ALLOWED_URL_HOSTS`；未配置时不限制微信会话或 HTTPS 主机。

### OCS 本机学习桥接

本功能通过已安装的 OCS（ocsjs）浏览器插件自动完成超星学习通的视频播放、章节切换和页面答题。
程序不打包、修改或依赖第三方 OCS 插件；插件已安装时与本项目伴随脚本协作，未安装时明确报告；即使第三方插件不支持当前超星页面，本项目仍由
自己的伴随脚本完成页面接管。所有可见操作统一在系统默认浏览器的同一个专用标签页中完成，不启动 Playwright
隔离浏览器，也不从浏览器提取账号、密码或 Cookie。操作者只需安装
[`wechat-claw-ocs-bridge.user.js`](src/main/resources/static/ocs/wechat-claw-ocs-bridge.user.js)；
Bot 会先打开带有短时任务标识的本机专用启动页，只有该标签页能租赁并确认学习命令，其他浏览器标签页无法
抢走任务。启动页会明确显示伴随脚本是否已经连接，未安装时提供安装入口；进入学习通后，右下角常驻控制面板
显示课程定位、登录等待、播放进度和答题结果。伴随脚本随后领取课程名称、可选课程引用和经过校验的学习通入口，
并把 HTML5
视频进度、页面标题、可用字幕和
平台适配器识别的结构化互动题作为有界事件回传。用户只需说“用 OCS 看高等数学并记录要点”；Bot 会打开
本机学习通课程列表。课程序号和引用只作为服务端当前会话的内部映射；手机端可直接说“第一门课”或“这门课”，
服务端会按微信会话有界保存对应的学习通课程 ID、班级 ID 和 `cpi`，再让伴随脚本直接进入目标课程。浏览器不会
把内部引用当成学习通页面编号；查看某个课程详情时还会标记本会话最近选中的课程，因此紧接着启动 OCS 可复用
稳定标识。用户说“看第三章”“打开绪论”时，`chapterName` 会随命令传给伴随脚本；进入课程后
自动点击对应章节，未指定章节时自动打开第一个未完成章节，不再停留在课程目录。引用在应用重启或清空会话后失效，需要重新“查课程”。没有已选课程时才按
名称查找并进入课程，无需用户复制网址。同名课程存在多个匹配且没有引用时，会提示重新查询课程并使用引用或更
完整的名称。名称匹配会统一大小写、全半角、空白和标点，课程名或班级名任一包含关键词即可；用于打开课程的
课程 ID、班级 ID 和 `cpi` 仍必须有效。学习状态保存在进程内，
重启后失效。

**前置条件：**

1. 默认浏览器安装 [Tampermonkey](https://www.tampermonkey.net/)
2. 在 Tampermonkey 中安装 [OCS 网课助手](https://github.com/ocsjs/ocsjs)（搜索「ocsjs」）
3. 应用启动后，访问 `http://127.0.0.1:8080/ocs/wechat-claw-ocs-bridge.user.js` 安装伴随脚本
学习通入口可能显示 HTML 课程卡片，也可能直接返回 `channelList` JSON。伴随脚本同时支持这两种官方页面形态，
浏览器只按课程名称解析页面；课程内部引用只在服务端解析，不交给浏览器猜测。JSON 只在当前浏览器
标签页内有界解析，不会整份回传给 Bot。

**工作流程：**

用户在微信中发送"帮我用 OCS 看 C17 高等数学"→ Bot 打开课程页（含 courseId/classId/cpi）→
OCS 插件自动启动 → 伴随脚本检测 OCS、注入配置、报告就绪 → OCS 负责播放/答题/换节 →
伴随脚本中继状态到 Bot → Bot 回传进度到微信。

**配置注入：** 伴随脚本通过 GM_setValue 写入 OCS 配置（视频复习、自动换节、答题模式、题库地址），
写入前保存当前值，会话结束后恢复。手机端可查询"OCS 插件已检测、配置已生效或缺少哪项"。

**登录闭环：** 浏览器有登录态时直接进入课程；登录失效时伴随脚本截取二维码发送到手机，
用户扫码后程序自动继续。

**题库对接：** 项目已有的 OcsQuestionAnswerer（DashScope AI）作为 OCS 的题库来源，
支持单选/多选/判断/填空，低置信度时发送到手机等待人工选择。

`powershell
${env:BOT_OCS_ENABLED} = 'true'
${env:BOT_OCS_AI_ANSWER_ENABLED} = 'true'
`

桥接接口只接受回环来源和伴随脚本专用请求头，不开放 CORS。学习状态保存在进程内，重启后失效。
配置项详见 `application.properties` 中 `bot.capability.learning.ocs.*`。

### 媒体与长回复

- 图片由 Qwen-Image 生成，短期 URL 会立即经过 HTTPS、主机、重定向、MIME 和大小校验后下载；
- 最近生成的图片在有界 TTL 和大小限制内作为下一轮模型的视觉上下文，工具生成图片也会在同一轮回传模型；
- 语音由 CosyVoice 生成，随后通过 FFmpeg 封装成微信可发送的视频；
- 明确的音色偏好会按会话恢复；
- 超过默认 1,500 字的最终文本直接作为 UTF-8 TXT 文件发送；
- 受限文本文件工具默认开启，内容只存在内存，模型不能指定服务端路径。

### 邮件安全

邮件工具主开关默认关闭，需显式设置 BOT_EMAIL_ENABLED=true 开启；开启后仍需会话 allowlist 和收件人 allowlist。第一次调用只保存并展示草稿，不连接
SMTP；用户下一条消息必须单独回复“确认发送”，系统才会发送服务端保存的同一份草稿。用户明确要求附上 PDF、Word
或其他最近生成文件时，文件会作为草稿附件进入同一审批流程；附件只在进程内有界短期保留。草稿默认 30 分钟过期，
可通过 `BOT_EMAIL_APPROVAL_EXPIRE_AFTER` 在 1 秒到 1 小时之间调整；任何修改都要重新准备。SMTP 一旦尝试，
无论结果明确与否都会消费审批，避免盲目重试造成重复邮件。只读 IMAP 待办来源的“是否需要行动”判断默认交给
模型批量分类（单批上限、独立超时和有界并发），模型不可用或失败时自动回退到确定性规则分类器。

## 当前已知限制与待加固项

以下内容是当前实现的边界，不应当当作已完成的生产级安全能力：

- `/api/wechat/**` 管理和主动发送接口没有管理员鉴权、来源限制或审计。应用默认只监听
  `127.0.0.1`；如果要改变监听地址，必须先补齐鉴权、CSRF/等效保护和资源归属校验。
- Multipart 上传目前由 Spring 统一限制为单文件/请求最多 50MB；各媒体接口没有独立的大小上限和完整 MIME
  魔数校验，控制器会把上传内容一次性读入内存，不适合直接暴露给不可信网络。
- `WECHAT_ILINK_MEDIA_DOWNLOAD_ENABLED=true` 后，入站媒体会落盘，但当前没有总容量、保留时间或定期清理策略，
  长期运行可能造成磁盘增长。
- 入站限流是进程内固定窗口计数；达到单会话上限的消息仍会先计入全局计数，而且同一会话 FIFO 的待处理队列
  没有独立容量上限。慢模型、关闭限流或突发流量下仍需增加按会话的背压/容量策略。
- 入站消息账本默认使用 SQLite，按 `bot_id + message_id` 记录领取租约、有限重试和完成状态；内存模式只提供进程内
  去重。它不能把 iLink 已推进但应用尚未回调完成的 SDK 游标变成完整断点恢复，也不能把出站发送变成严格 exactly-once。
- 出站回复失败不会自动重放可能已经到达微信的副作用请求；当前只能依赖当前回复窗口或后续入站窗口继续处理，
  生产级出站 outbox 仍需要单独的 Channel 适配设计。
- 情感画像只保存在进程内；画像内容来自模型回复末尾的标记，并会进入后续系统提示，目前仍需增加更严格的内容
  白名单/隔离，防止模型输出污染长期上下文。
- `BOT_PROFILE_CARE_ENABLED=true` 后，主动关怀任务每 30 分钟扫描负面情绪画像；当前没有“用户已暂停互动”的时间
  判断或发送去重，且多 Bot 场景会尝试当前已登录会话，适合演示验证，不适合直接作为生产级主动触达策略。

## 会话记忆

默认使用 SQLite `ConversationMemory`。应用会自动创建 `./data/wechat-bot.db` 并初始化 schema；如仅需临时运行，设置
`BOT_MEMORY_TYPE=memory`。DDL 副本位于 [`docs/sqlite-schema.sql`](docs/sqlite-schema.sql)，SQL 只位于
[`ConversationMessageMapper.xml`](src/main/resources/mapper/ConversationMessageMapper.xml)。SQLite 使用 WAL 模式和单连接写入，
适合当前单实例自托管部署。

同一数据库还包含 `ilink_inbound_message` 账本，用于按 Bot 与 iLink `message_id` 去重、租约恢复和失败退避；账本只保存
身份与状态，不保存消息正文、媒体或 `context_token`。应用重启后只有在 iLink 重新投递消息时才能继续处理；活跃 SDK Client、
登录凭据和游标仍不会持久化。

稳定会话键为 `wechat-ilink|bot=<BotID>|user=<微信用户ID>`：Bot ID 是租户边界，避免不同 Bot
把同一微信用户的历史、画像或待处理任务混在一起；不会使用扫码页面 Session ID。开发阶段切换到新键后不迁移旧
数据库数据，旧数据会自然失效。`/clear` 会清除对话历史、最近图片/文件记忆和待确认邮件。微信 SDK 的活跃
Client 不会被持久化，应用重启后仍需重新扫码。

上下文达到消息条数或字符阈值后，`ConversationContextCompactor` 会先把较早消息按“用户/助手”标签合并，保留
原文内容，只减少消息边界带来的结构开销；无损结果仍放不下时，才使用同一个 `AgentModelClient` 在无工具模式下
生成有损摘要。摘要保存在 `bot_conversation_summary`，并作为不可信的用户上下文注入，不能覆盖系统规则。摘要请求
失败时不阻断本轮回复，只保留能放入上限的最近原文；`/clear` 会同时清除摘要。

## HTTP 接口

Bot 会话：

```text
GET    /api/wechat/bots
POST   /api/wechat/bots
GET    /api/wechat/bots/{sessionId}/login/status
GET    /api/wechat/bots/{sessionId}/login/qr-code
POST   /api/wechat/bots/{sessionId}/login/refresh
DELETE /api/wechat/bots/{sessionId}
```

项目不提供主动发送消息接口。文字和媒体只能回复一条带有效 `context_token` 的真实入站消息；回复上下文
绑定当前 SDK Client、Bot、源消息和目标用户，并在本轮处理完成后失效。后台任务只能保存有界的待回复内容，
等用户下一次发送消息后再附带回复。Bot 会话管理接口部署到可访问网络前仍需增加管理员鉴权、来源限制、
CSRF/等效保护和审计，不能仅依赖默认的本机监听。

## 构建与验证

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

架构测试会阻止旧分层包恢复、SDK 类型越界、第二套 AI SDK 混入、Java 内出现 MyBatis SQL，以及关键模块
依赖倒置被破坏。
