# PRD: mapping.json 驱动的迁移流程（MOOC fork）

状态：待实现
范围：MOOC fork 本身的改动 + 管理员侧 wrapper（不含认证服务器侧实现）

---

## Problem Statement

我在运营一个第三方 Yggdrasil 认证服务器，玩家在我的服务上拥有 v4 UUID。现在有两类真实需求：

1. **接入**：一个原本跑离线模式的老服想接入我的服务。它的世界文件、`ops.json`、`whitelist.json`、`usercache.json` 里存的都是离线 UUID（`f(玩家名)`），需要全部迁移成我签发的 v4 UUID，否则玩家的背包、成就、权限全部对不上。
2. **脱离**：一个已经接入我服务的服想离开，需要把 v4 UUID 全部还原成离线 UUID。

原版 MOOC 能做文件迁移，但它的 UUID 来源不适合我：

- 它的接入方向（离线 → 在线）默认查询 **Mojang** API，会给玩家发**正版 UUID**而不是我的 UUID。这是**静默错误**——转换"成功"了，但接错了身份体系。
- 它能用的名字来源只有 `usercache.json`，而那是**缓存**（有容量上限、条目会过期），不是全量花名册。老服上必然有玩家数据文件存在但不在 usercache 里，这些会被跳过。
- 它无法消费我按自己玩家表导出的映射数据。
- 它的 HTTP 层不发送任何请求头，导致我无法给它一个需要鉴权的接口；而我又不能把凭证编进要分发的构件里。
- 它构建目标是 Java 26，而游戏服普遍跑 JDK 17/21，产物根本起不来。

另外，MOOC 会**原地改写**世界文件和根目录名单 JSON，不锁文件、不检查服务端是否在运行。对一个交给非技术管理员使用的工具，这是不可接受的风险。

## Solution

把 MOOC 的**数据入口收敛成唯一一个文件**：`mapping.json`。由我的认证服务器从玩家表导出（含名字历史），管理员通过一次性令牌下载，MOOC 只读这个本地文件，**全程不发起任何网络请求**。

为此删除 MOOC 原有的整个 HTTP/UUID 查询子系统（`HttpGet`、`ProfileApiConfig`、`OnlineProfileLookup`、`PrefetchUsercache`，以及随之失效的 `UsercacheFile`）。这不是妥协，而是净简化：删掉之后，`ConverterV3` 和 `ConvertFtbQuests` 里两份重复的 `resolveTargetUuid` 退化成一次纯查表，`usercache.json` 依赖、Mojang 默认 API、`System.exit(0)` 地雷一起消失。

同时提供一个管理员侧的 wrapper，在真正改动文件**之前**强制通过安全闸门（服务端已停机、备份已就绪）、校验构件与映射的完整性、展示映射预览并要求显式确认，最后以子进程方式调用 MOOC 完成转换。

产物的 Java 目标降到 17，并加一条"真的在 JDK 17 上启动一次"的 CI 冒烟测试——因为**编译通过和测试通过都不能证明能启动**（见 Implementation Decisions 中的实证）。

## User Stories

### 管理员：接入一个离线服

1. 作为游戏服管理员，我想在认证服的迁移页面上选择"接入"方向，这样我不需要理解 UUID 版本或文件布局。
2. 作为游戏服管理员，我想用一次性令牌下载 `mapping.json`，这样我的玩家名册不会因为一个长期有效的凭证而泄漏。
3. 作为游戏服管理员，我想同时下载到 MOOC 构件和它们的 SHA-256，这样我能确认拿到的东西没被篡改。
4. 作为游戏服管理员，我想让工具**拒绝**在我没有停服的情况下运行，这样我不会损坏世界文件。
5. 作为游戏服管理员，我想让工具**拒绝**在我没有提供有效备份的情况下运行，这样我出错了还能回滚。
6. 作为游戏服管理员，我想在改动前看到"将要迁移 N 个玩家"的清单和抽样条目，这样我能判断映射对不对。
7. 作为游戏服管理员，我想看到映射里声明的方向与我的选择不一致时被拒绝，这样我不会把离线服配上正版 UUID。
8. 作为游戏服管理员，我想在确认前看到 MOOC 检测出的服务端类型、世界目录结构、存档格式，这样我能发现自动检测错了。
9. 作为游戏服管理员，我想在确认后由工具自动完成全部文件改写，包括 `ops.json`、`whitelist.json`、`banned-players.json`、`usercache.json`，这样玩家的权限不会丢。
10. 作为游戏服管理员，我想在转换结束后拿到明确的成功/失败结论，这样我不需要去读日志文件猜。
11. 作为游戏服管理员，我想在"服务器太老、本来就不需要转换"时得到一个**可区分的**退出状态，而不是和"转换成功"一样的 0。

### 管理员：脱离我的服务

12. 作为游戏服管理员，我想选择"脱离"方向，把服上所有 v4 UUID 还原成离线 UUID。
13. 作为游戏服管理员，我想用同一份 `mapping.json` 完成脱离，这样我不需要为两个方向各准备一份数据。
14. 作为游戏服管理员，我想在脱离时同样受停服检测、备份校验和预览确认的保护。

### 我的认证服务器（作为数据生产者，本 PRD 不实现，只定义契约）

15. 作为认证服务器运营者，我想让导出的 `mapping.json` 在文件头声明方向，这样工具能替我拦住方向用反的错误。
16. 作为认证服务器运营者，我想让 `mapping.json` 包含账号的**全部历史名字**，因为离线服上改过名的玩家会留下多个离线 UUID 文件。
17. 作为认证服务器运营者，我想让一次性令牌由服务端强制单次使用且短时有效，这样下载行为可以被审计且无法重放。

### 维护者：代码与构建

18. 作为这个 fork 的维护者，我想删掉整个 HTTP/UUID 查询子系统，这样代码路径和依赖都变少。
19. 作为这个 fork 的维护者，我想让产物的 Java 目标是 17，这样 JDK 17/21 的游戏服能直接跑，而我仍然可以用 JDK 25 构建。
20. 作为这个 fork 的维护者，我想让 CI 在 JDK 17/21/25 上各启动一次 jar，这样"编译通过但起不来"的问题不会再溜过去。
21. 作为这个 fork 的维护者，我想让发布产物钉在版本 tag 上而不是 `latest`，因为这个工具会改写生产环境的世界文件。
22. 作为这个 fork 的维护者，我想让 mapping 解析和方向校验有独立测试，这样我改动转换逻辑时不会碰坏数据入口。

## Implementation Decisions

### 已完成的改动（本次对话中已实现并实测）

- **Java 17 目标**。三处源码改动：`MinecraftUuids` 的 `catch (… _)` 匿名变量（Java 22 语法）改名；`PluginMetadata.clampPriority` 的 `Math.clamp`（Java 21 API）改为 `Math.max`/`Math.min` 组合；`Main.main` 由包级私有改为 `public static void main`。构建配置保留 JDK 26 toolchain，新增 `options.release = 17`，因此用 JDK 25 即可构建出 17 目标产物，CI 的 `setup-java` 无需改动。
  - **`Main.main` 那一处是运行时才暴露的**：编译和测试全部通过，但在 JDK 17 上启动时报"找不到 main 方法"。JDK 25 的启动器放宽了 main 方法要求，所以旧代码一直没暴露。这直接证明了 CI 必须真启动一次 jar。
- 实测结论：产物 class file major version 61（Java 17），在 JDK 17.0.16 与 21.0.12 上均可正常启动并输出帮助/版本；现有测试套件全绿。

### 新增深层模块：`MappingTable`

数据入口的唯一实现。职责：读取并校验 `mapping.json`，对外只暴露"方向 + 可直接灌入上下文的 from→to 映射表 + 用于预览的条目列表"。

对外接口（简单且很少变动）：

- `load(Path file)` → 解析并校验，失败即抛错
- `direction()` → 文件声明的迁移方向
- `uuidMap()` → 已按方向定向好的 `from → to` 映射
- `entries()` → 预览用的条目列表（名字 + 两端 UUID）
- `size()`

它封装掉的全部复杂度：JSON 解析、schema 版本校验、**方向语义**（哪个字段是 `from`、哪个是 `to`）、**自洽性校验**（`offlineFromName(name) == offline`）、重复条目检测。调用方完全看不到这些。这个模块只依赖文件内容，可完全独立测试。

**为什么这个模块值得深**：方向判断错一次就会把离线服的权限文件写成第三方 UUID。把方向语义关在一个纯函数式的模块里，是这次改动里唯一能防止该类事故的结构性手段。

### `mapping.json` 契约（与认证服务器侧的接口边界）

```json
{
  "version": 1,
  "direction": "offline-to-thirdparty",
  "serverId": "...",
  "generatedAt": "2026-09-22T11:05:00Z",
  "entries": [
    { "name": "Tester", "offline": "<v3>", "online": "<v4>" }
  ]
}
```

- `direction` 取值 `offline-to-thirdparty`（接入）或 `thirdparty-to-offline`（脱离）。
- 两端 UUID 同时提供，因此**一份文件同时支持两个方向**；`direction` 只用于与命令行选择做一致性校验，不用于推导映射。
- `offline` 字段必须等于 `offlineFromName(name)`，由 `MappingTable` 强制校验。若账号有历史改名，认证服务器必须**每个历史名字导出一条**，该校验才成立。
- UUID 允许带连字符或 32 位无连字符两种写法（沿用现有的宽松解析）。
- 文件本身应附带 SHA-256 或签名，由 wrapper 校验（一次性令牌保护的是授权与保密，不覆盖完整性）。

### 删除清单

- 删除 `HttpGet`、`ProfileApiConfig`、`OnlineProfileLookup`、`PrefetchUsercache`。
- 连带删除 `UsercacheFile` 及其测试（唯一调用方是 `PrefetchUsercache`）。
- 删除 CLI 选项 `-customApiBaseUrl`、`-retrieveUUIDUrl`、`-retrieveNameUrl`，以及解析它们的逻辑。
- 从插件注册表的 discovery 阶段移除 `PrefetchUsercache`。
- 保留 `MinecraftUuids`：`offlineFromName` 改由 `MappingTable` 的自洽性校验使用，`parse` 由 mapping 解析使用，`dashless` 仍被 FTB Quests 插件使用。

### 转换路径的简化

- `ConverterV3` 与 `ConvertFtbQuests` 各自的 `resolveTargetUuid` 退化为"查上下文映射表，查不到则告警并跳过"，两份重复实现随之消失。这也消除了原先"只有 ONLINE 方向返回 null"的不对称。
- 映射表预先填好之后，转换过程中**不再有任何网络调用**，`ConverterV3` 里那段解释 usercache 局限性的长注释也随之删除。

### 退出状态约定

wrapper 依赖可区分的退出状态，因此把现有的两处提前 `System.exit(0)` 改为语义明确的码：

- `0` 转换成功
- `1` 参数/用法错误（沿用现状）
- `2` 被预检闸门拒绝（wrapper）
- `3` mapping 无效或方向不一致
- `4` 构件或映射完整性校验失败（wrapper）
- `5` 存档格式过老、无需转换（替换 `DetectSaveFileFormat` 中的 `System.exit(0)`）
- `6` 接入方向无可用映射而中止（替换 `PluginOrchestrator` 中的中止分支）

`PluginOrchestrator` 现有的"接入方向映射为空则中止"检查保留并加强——在 HTTP 路径删除后，它是防止空/错映射写入文件的最后一道闸门。

### Wrapper 模块

以**子进程**方式调用 MOOC 构件，不做类库嵌入（换取 Java 版本无关与崩溃隔离）。

- **预检闸门**（sealed 接口 + 实现），在任何文件改动前运行，每个闸门返回"通过 / 拒绝 + 原因 + 补救建议"：
  - `SessionLockGate`：检测世界是否被运行中的服务端持有。
  - `BackupGate`：要求提供备份路径，校验其存在、非空、且结构像一份服务端备份。
- **`ArtifactVerifier`**：对构件与 `mapping.json` 做 SHA-256 校验。
- **`MappingPreview`**：**只依据 `MappingTable` 生成**（条目数 + 抽样条目 + 两端 UUID），不做文件系统 dry-run。理由：真正的 dry-run 需要复刻 `ConverterV3` 的匹配逻辑，重复实现的风险高于收益；而映射文件本身就是最准确、可人工复核的预览材料。
- **方向一致性强制**：`mapping.json` 声明的方向必须与管理员的选择一致，不一致直接拒绝。这堵住了"世界文件不动、但根目录 JSON 被写成反向 UUID"的漏洞（根目录文件的改写不检查 UUID 版本）。
- **退出状态映射**：把上述状态码翻译成给管理员看的结论与下一步建议。

### CI 与发布

- 新增**启动冒烟测试**：在 JDK 17 / 21 / 25 的 matrix 上执行构件并断言退出码为 0、输出含版本号。这是本次改动中被实证必需的一条（见上文 `Main.main`）。
- 保留现有测试与 javadoc 任务；构建 JDK 保持 26 不变。
- 发布产物**钉在版本 tag** 上并附 SHA-256（或使用 GitHub artifact attestation），生产使用不提供 `latest`。

## Testing Decisions

**好测试的判据**：只验证外部可观察行为，不验证实现细节。例如 `MappingTable` 的测试喂入文件内容、断言解析结果或抛出的异常类型，而不去断言内部解析步骤或私有字段。

**测试风格先例**（沿用现有约定）：JUnit 5，`@TempDir` 提供临时文件系统，`@Nested` 按被测方法分组，方法名用 `行为_when_条件`，fixture 用文本块，错误路径用 `assertThrows`，测试类放在与被测类相同的包路径下。先例见 `UsercacheFileTest`、`ServerPropertiesFileTest`。

**要写测试的模块**：

1. `MappingTable` —— 两个方向各自的有效文件、方向字段非法、缺字段、JSON 格式错误、`offlineFromName(name)` 不匹配、重复条目、未知 schema 版本、两种 UUID 写法。全部以"输入文件内容 → 结果或异常"的形式。
2. `SessionLockGate` / `BackupGate` —— 用 `@TempDir` 构造合成的服务端目录（`server.properties` + 世界目录，分别带/不带被持有的 `session.lock`；备份目录分别完整/缺失/为空），断言通过或被拒绝。合成目录的形状可直接复用本次对话中用于端到端验证的那套 fixture。
3. `ArtifactVerifier` —— 已知内容对应已知摘要；摘要不匹配时失败。

**要写的 CI 测试**：JDK 17/21/25 三档启动冒烟。

**明确不测**（依据本次决定）：MOOC 既有的转换内部逻辑（`ConverterV3` 与各转换插件）、认证服务器侧实现。

## Out of Scope

- **认证服务器侧的全部实现**：`mapping.json` 的生成接口、一次性令牌的服务端语义、迁移工具页面、玩家表与名字历史的维护。本 PRD 只定义 `mapping.json` 契约作为接口边界。
- `-copy`、`-properties` 及 FTB Quests / NBT 相关行为本身的改动。
- 1.7.6 之前的版本（沿用 MOOC 现有立场）。
- 与玩家绑定的实体关系（宠物归属等）——MOOC 已知限制。
- **打包 jlink 运行时**：Java 目标降到 17 后，只有 1.18 之前的服务端才需要；本次不做。若将来要做，注意产物是平台相关的（win/linux/mac × 架构），且裁剪运行时需要手动补 `jdk.unsupported`（logback 依赖），不能只按 `jdeps` 的输出裁剪。
- 与上游仓库保持可合并性。删除 HTTP 子系统后与上游分叉较大，这是本次决定接受的代价。

## Further Notes

- **`session.lock` 的位置需要按支持的 MC 版本确认**：经典布局下它在世界目录内，但 2026 年引入的 `dimensions/` 布局下位置可能不同。这是 `SessionLockGate` 实现前必须先验证的事项，不要凭假设写死。
- **`mooc_logs/` 会落在工作目录**：MOOC 的日志配置把日志写到进程工作目录下的 `mooc_logs/`。在"在服务端目录下运行"这个使用方式下，会在服务端目录里生成该文件夹。wrapper 要么接受，要么显式告知管理员。
- **根目录名单文件的改写是"全表扫描"式**：MOOC 对 `ops.json` 等 6 个文件，逐条把映射表里的 `from → to` 做字符串替换。使用全量花名册映射时，复杂度是"条目数 × 文件大小"。对现实规模没问题，但如果花名册达到数万条，值得重新评估。
- **名字历史是数据侧的前置条件**：离线服上玩家改一次名就换一次 UUID，因此同一个人的旧数据会挂在旧名字派生的 UUID 下。认证服务器必须导出历史名字，否则这些旧数据永远无法迁移——这是流程能否成功的关键业务约束，不是技术细节。
- **本次改动的一个意外收益**：删除 HTTP 子系统后，原先"如何让 MOOC 支持 Yggdrasil 的 POST 批量查询"这个问题彻底消失了，因为工具不再需要查询任何东西。
