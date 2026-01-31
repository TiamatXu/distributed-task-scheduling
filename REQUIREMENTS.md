# 分布式任务调度与执行系统 - 需求清单

本文档根据项目初始设计文档 `1-distributed-task-scheduling.md` 提炼而成，旨在为您提供一份清晰、可执行的开发需求列表，帮助您一步步构建整个系统。

## 一、 项目愿景

构建一个健壮、可扩展的分布式任务调度平台。该平台能够通过 API 接收异步任务，由中央协调器调度给最合适的执行节点（Agent），执行各类脚本或应用，并可靠地回传执行结果。

## 二、 核心功能需求 (按模块拆解)

### 1. 任务接入层 (`task-api`)

该模块是系统的门户，负责接收所有来自客户端的请求。

- **[F-API-01] 提交任务**: 提供一个 `POST /api/v1/tasks` RESTful 接口，用于创建新任务。
    - **输入**: 任务名称、任务类型（如 `SHELL`）、执行内容、超时时间、重试次数等。
    - **输出**: 返回一个全局唯一的 `taskId` 和任务初始状态 `PENDING`。

- **[F-API-02] 查询任务**: 提供一个 `GET /api/v1/tasks/{taskId}` 接口，用于查询指定任务的状态和结果。
    - **输出**: 任务的当前状态 (`PENDING`, `RUNNING`, `SUCCESS`, `FAILED` 等)、执行结果、日志信息。

- **[F-API-03] 取消任务**: 提供一个 `DELETE /api/v1/tasks/{taskId}` 接口，用于请求取消一个正在排队或运行中的任务。

- **[F-API-04] 重试任务**: 提供一个 `POST /api/v1/tasks/{taskId}/retry` 接口，用于手动触发一个失败任务的重试。

### 2. 任务管理核心 (`task-manager`)

该模块是系统的大脑，负责管理所有任务的生命周期。

- **[F-MGR-01] 任务状态机**: 定义并实现任务的完整生命周期状态流转：`PENDING` (待处理) → `SCHEDULED` (已调度) →
  `DISPATCHED` (已分发) → `RUNNING` (运行中) → `SUCCESS` (成功) / `FAILED` (失败) / `CANCELLED` (已取消)。

- **[F-MGR-02] 任务存储**: 使用 **Redis** 作为核心存储。
    - 每个任务的详细信息（名称、类型、状态、结果等）应存储在 Redis Hash 中 (例如 `task:{taskId}`)。
    - 保证任务状态变更的原子性。

- **[F-MGR-03] 幂等性保证**: 支持客户端在提交任务时传入唯一的 `requestId`，防止因网络问题等原因造成的重复提交。

### 3. 调度与协调模块 (`task-scheduler`)

该模块是系统的调度中心，负责决定哪个任务由哪个节点来执行。

- **[F-SCH-01] 节点动态发现**: 必须能够自动发现和管理所有在线的执行节点 (Agent)。
    - 监听 **ZooKeeper** 的特定路径 (如 `/live-agents`) 来实时获取可用 Agent 列表。

- **[F-SCH-02] 领导者选举**: 在部署多个 Scheduler 实例时，必须通过 **ZooKeeper** 选举出唯一的 Leader。只有 Leader
  实例能执行任务调度逻辑，其他实例作为备用。

- **[F-SCH-03] 调度策略**: 实现可插拔的调度算法。
    - **必选**: 随机策略 (Random) - 随机选择一个可用 Agent。
    - **可选**: 轮询策略 (Round-Robin)、最低负载策略 (Least-Load)。

- **[F-SCH-04] 失败转移 (Failover)**: 必须能够处理 Agent 节点掉线的情况。
    - 通过 ZooKeeper 临时节点机制检测 Agent 的下线。
    - 将分配给掉线 Agent 且正在运行中的任务重新置为待调度状态，等待下一次调度。

### 4. 执行节点 (`task-agent`)

该模块是系统真正干活的手和脚，负责执行具体的任务。

- **[F-AGT-01] 节点注册**: 启动时，必须向 **ZooKeeper** 的 `/live-agents` 路径下注册一个代表自己的临时节点。

- **[F-AGT-02] 心跳上报**: 必须定期向消息队列 (**Kafka**) 的特定 Topic (如 `agent-heartbeat-topic`)
  发送心跳消息，表明自己处于存活状态，并可携带当前负载信息。

- **[F-AGT-03] 任务消费**: 必须能从 **Kafka** 的任务分发 Topic (如 `task-dispatch-topic`) 中消费分配给自己的任务。

- **[F-AGT-04] 任务执行**: 必须支持根据任务类型执行不同逻辑。
    - **必选**: `ShellTaskExecutor` - 能够执行 Shell 命令。
    - **可选**: `PythonScriptExecutor` - 能够执行 Python 脚本。

- **[F-AGT-05] 状态与结果上报**: 必须在任务执行的不同阶段，将状态和结果通过 **Kafka** (如 `task-status-update-topic`)
  上报给任务管理器。
    - 开始执行时上报 `RUNNING`。
    - 结束后上报 `SUCCESS` 或 `FAILED`，并附带执行结果或错误信息。

### 5. 消息与通信

这是连接系统各个模块的血管和神经。

- **[F-BUS-01] 消息总线**: 必须使用 **Kafka** 作为系统各模块间异步通信的桥梁。
    - `task-dispatch-topic`: 用于调度器向 Agent 分发任务。
    - `task-status-update-topic`: 用于 Agent 向 Manager 上报状态和结果。
    - `agent-heartbeat-topic`: 用于 Agent 上报心跳。

- **[F-RPC-01] 服务调用**: 模块间的同步调用（如果需要），使用 **Dubbo** 框架。
    - `task-api` 调用 `task-manager` 的服务。
    - ZK 作为 Dubbo 的注册中心。

## 三、 非功能性需求

- **[NF-01] 高可用**: Scheduler 和 Agent 都应是无状态的或可快速恢复的，支持多实例部署。系统不应存在单点故障。
- **[NF-02] 可扩展性**:可以通过简单地增加 Agent 节点数量来水平扩展系统的任务执行能力。
- **[NF-03] 健壮性**: 系统需要优雅地处理各种异常，如节点宕机、网络分区、消息丢失（通过合理配置 Kafka）等。
- **[NF-04] 可观测性**: 系统应输出详细的日志，并预留关键指标（Metrics）接口，以便于后续接入监控系统 (如 Prometheus +
  Grafana)。

## 四、 技术栈选型

| 领域       | 技术            | 作用                         |
|----------|---------------|----------------------------|
| 服务协调与注册  | **ZooKeeper** | Agent 注册、Scheduler 选主、分布式锁 |
| 缓存与状态存储  | **Redis**     | 存储任务的实时状态和详细信息             |
| 消息队列     | **Kafka**     | 任务分发、状态回传、心跳通信             |
| RPC 框架   | **Dubbo**     | Java 模块间内部服务调用             |
| 核心后端语言   | **Java**      | (Spring Boot)              |
| 核心执行节点语言 | **Python**    | (或其他脚本语言)                  |

## 五、 分阶段开发路线图 (Roadmap)

**强烈建议** 按照以下阶段进行开发，这能帮助您平滑学习曲线，逐步构建系统，降低复杂度。

### ✅ 第一阶段：MVP - 跑通核心流程

- **目标**: 实现单个 Agent 从 Redis 获取并执行任务，然后将结果写回 Redis。
- **技术**: `Java/Python`, `Redis`
- **步骤**:
    1. 开发 `task-agent`，实现一个 `ShellTaskExecutor`。
    2. 在 Redis 中手动 `LPUSH` 一个任务（一个 JSON 字符串）。
    3. `task-agent` 通过 `BRPOP` 从 Redis 队列中拉取任务并执行。
    4. Agent 将任务的 `RUNNING`, `SUCCESS`/`FAILED` 状态回写到 Redis 的一个 Hash 中。
    5. **验证**: 通过 Redis 客户端检查任务状态是否正确变更。

### ⏩ 第二阶段：引入分布式协调

- **目标**: 实现多个 Agent 的自动注册和 Scheduler 的领导选举。
- **技术**: `ZooKeeper`
- **步骤**:
    1. `task-agent` 启动时向 ZK 注册一个临时节点。
    2. 开发 `task-scheduler`，实现 ZK 领导选举。
    3. Leader Scheduler 监听 ZK，动态维护一个可用的 Agent 列表。
    4. 改造流程：任务提交后先进入 Redis 的一个“待调度”队列。
    5. Scheduler 从“待调度”队列取任务，随机选择一个 Agent，并将任务放入该 Agent 的专属队列中。

### ⏩ 第三阶段：全面异步与解耦

- **目标**: 使用 Kafka 替代 Redis 队列，作为标准的通信总线。
- **技术**: `Kafka`
- **步骤**:
    1. Scheduler 调度成功后，将任务消息发送到 Kafka 的 `task-dispatch-topic`。
    2. Agent 改造为 Kafka Consumer，订阅自己的 Topic 来接收任务。
    3. Agent 的所有状态更新（包括心跳）全部通过向 Kafka 发送消息来完成。
    4. 开发 `task-manager`，作为独立的消费者，订阅状态更新 Topic，并将最新状态写入 Redis。

### ⏩ 第四阶段：服务化与 API

- **目标**: 构建正式的 API 层，并用 Dubbo 治理内部服务。
- **技术**: `Spring Boot`, `Dubbo`
- **步骤**:
    1. 创建 `task-api` 模块，暴露 RESTful 接口。
    2. 将 `task-manager` 的核心逻辑封装成 Dubbo 服务。
    3. `task-api` 作为 Dubbo Consumer 来调用这些服务。
    4. 统一使用 ZK 作为 Dubbo 的注册中心。

### ⏩ 第五阶段：健壮性与可观测性

- **目标**: 增加监控、日志、告警和更完善的失败恢复机制。
- **步骤**:
    1. 引入统一日志框架，并考虑日志聚合。
    2. 完善 Scheduler 的 Failover 逻辑。
    3. 引入 Prometheus + Grafana 进行系统监控。
    4. (可选) 开发一个简单的管理前端。

---
**现在，您可以从 “第一阶段：MVP” 开始，动手编写您的第一个模块了！**
