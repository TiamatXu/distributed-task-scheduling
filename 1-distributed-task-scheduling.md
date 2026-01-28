# 项目一：分布式“任务调度与执行系统”

## 概念

**异步任务调度平台**：  
前端（API）提交任务 → 后端协调调度 → 分布式节点执行 → 回传结果。

## 技术整合

| 技术点           | 作用                                       |
|---------------|------------------------------------------|
| **ZooKeeper** | 注册“任务执行节点”（Agent），实现分布式锁、选主。             |
| **Redis**     | 存放任务缓存、执行状态、心跳。                          |
| **Kafka**     | 任务消息分发、任务结果上报事件流。                        |
| **Dubbo**     | Java 版提供 RPC 服务：任务管理、节点通信。               |
| **Python**    | 作为执行节点（代理主机），用 Python 执行脚本／算法任务。         |
| **设计模式**      | 工厂（任务实例化）、观察者（监听任务事件）、策略（调度策略）、代理（节点调用）。 |

## 学习收获

* 消息驱动架构（Kafka）
* 服务注册发现（ZK + Dubbo）
* 缓存与状态同步（Redis）
* 跨语言 RPC（Java 调 Python）
* 每个模块都可以做设计模式的应用演示

## 扩展

后续还能演化成分布式爬虫平台、AI 任务调度系统、批处理系统。

## 功能模块拆解

### 1️⃣ 任务接入层（Task API）

* 提交任务（同步 / 异步）
* 查询任务状态
* 取消 / 重试任务

**技术**：

* Java：Spring Boot + Dubbo Provider
* Python：可做轻量 Client

**设计模式**

* 命令模式（TaskCommand）
* DTO / Facade

### 2️⃣ 任务管理核心（Task Manager）

* 任务生命周期管理（PENDING / RUNNING / SUCCESS / FAILED）
* 任务拆分（大任务 → 子任务）
* 幂等控制

**技术**

* Redis（状态机、TTL）
* 本地状态机 + Redis 同步

**设计模式**

* 状态模式
* 模板方法（统一任务执行流程）

### 3️⃣ 调度与协调模块（Scheduler）

* 节点选择
* 负载均衡
* 失败转移
* 分布式锁 / 选主

**技术**

* ZooKeeper（节点注册、锁）
* 可替换调度策略

**设计模式**

* 策略模式（调度算法）
* 单例（ZK Client）

### 4️⃣ 消息总线（Event Bus）

* 任务下发
* 执行结果回传
* 事件通知

**技术**

* Kafka
* Topic：task-dispatch / task-result / task-event

**设计模式**

* 观察者模式
* 发布-订阅

### 5️⃣ 执行节点（Agent / Worker）

* Python Agent（执行脚本、算法）
* Java Agent（业务任务）

**能力**

* 心跳上报
* 拉取任务
* 上报结果

**设计模式**

* 工厂模式（TaskExecutor）
* 责任链（前置校验 → 执行 → 回传）

### 6️⃣ RPC 通信层

* 调度中心 ↔ 执行节点
* 管理服务 ↔ 查询服务

**技术**

* Dubbo（Java 主）
* Python Dubbo Client / 或 REST 替代

**设计模式**

* 代理模式（RPC Stub）

## 总结

> 这是一个「教科书级分布式系统练功项目」，你提到的所有中间件都不是“硬塞”，而是天然需要。

---

## 详细功能模块清单

### 1. 任务接入层 (Task API) - `task-api` 模块

- **RESTful API (基于 Spring Boot)**:
  - `POST /api/v1/tasks`: 提交新任务。
    - Request Body:
      `{ "taskName": "...", "taskType": "SHELL|PYTHON_SCRIPT", "payload": "...", "executionTimeout": 60, "retryCount": 3 }`
    - Response: `{ "taskId": "uuid-...", "status": "PENDING" }`
  - `GET /api/v1/tasks/{taskId}`: 查询任务状态和结果。
    - Response: `{ "taskId": "...", "status": "...", "result": "...", "logs": [...] }`
  - `DELETE /api/v1/tasks/{taskId}`: 请求取消任务。
    - Response: `{ "message": "Cancel request accepted." }`
  - `POST /api/v1/tasks/{taskId}/retry`: 请求重试任务。
    - Response: `{ "newTaskId": "uuid-...", "status": "PENDING" }`
- **RPC 接口 (基于 Dubbo)**:
  - `TaskService.submit(TaskDTO)`
  - `TaskService.getTaskInfo(String taskId)`

### 2. 任务管理核心 (Task Manager) - `task-manager` 模块

- **任务状态机**:
  - 定义状态: `PENDING`, `SCHEDULED`, `DISPATCHED`, `RUNNING`, `SUCCESS`, `FAILED`, `CANCELLED`
  - 使用 Redis Hash 存储任务详细信息: `HMSET task:{taskId} name "..." type "..." status "..."`
  - 任务状态流转的原子性保证 (可使用 Lua 脚本或 Redis Transactions)。
- **任务持久化**:
  - 初始版本可只用 Redis。
  - 进阶版本可将任务的最终状态和结果异步写入数据库 (如 MySQL/PostgreSQL)。
- **幂等性控制**:
  - 在提交任务时，允许客户端提供一个唯一的请求ID (`requestId`)，服务端通过检查 `requestId` 是否已处理来防止重复提交。

### 3. 调度与协调模块 (Scheduler) - `task-scheduler` 模块

- **节点发现**:
  - 监听 ZooKeeper 路径 `/live-agents` 下的子节点变化，动态维护可用 Agent 列表。
- **领导选举 (Leader Election)**:
  - 多个 Scheduler 实例启动时，通过在 ZooKeeper 创建临时节点 (e.g., `/scheduler-leader`) 来选举唯一的 Leader，只有 Leader
    负责执行调度逻辑。
- **调度策略 (Strategy Pattern)**:
  - **Random**: 随机选择一个可用 Agent。
  - **RoundRobin**: 轮询选择。
  - **LeastLoad**: 选择负载最低的 Agent (需 Agent 上报心跳时携带负载信息)。
- **失败转移 (Failover)**:
  - 通过 ZK 监听 Agent 的临时节点，如果节点消失（会话超时），则认为 Agent 失联。
  - 将该 Agent 上正在执行的 `RUNNING` 状态的任务重新置为 `PENDING` 或 `SCHEDULED`，等待重新调度。

### 4. 消息总线 (Event Bus) - 使用 Kafka

- **Topic 规划**:
  - `task-dispatch-topic`: 用于调度器向执行节点下发任务。
  - `task-status-update-topic`: 用于执行节点向任务管理器上报状态更新 (如 `RUNNING`, `SUCCESS`, `FAILED`)。
  - `agent-heartbeat-topic`: 用于执行节点定期上报心跳。

### 5. 执行节点 (Agent / Worker) - `task-agent` 模块 (可有 Java/Python 两个版本)

- **心跳上报**:
  - 启动后向 ZooKeeper 注册临时节点 `/live-agents/{agent-id}`。
  - 定期 (如每 10 秒) 向 `agent-heartbeat-topic` 发送心跳消息，包含 `agent-id` 和当前负载 (如 CPU/内存使用率、当前任务数)。
- **任务消费**:
  - 订阅 `task-dispatch-topic`，获取分配给自己的任务。
- **任务执行器 (Factory & Strategy Pattern)**:
  - 根据任务类型 (`taskType`) 创建不同的执行器。
    - `ShellTaskExecutor`: 执行 shell 命令。
    - `PythonScriptExecutor`: 执行 Python 脚本。
- **状态与日志上报**:
  - 任务开始执行时，向 `task-status-update-topic` 发送 `RUNNING` 状态。
  - 任务执行过程中，将标准输出和错误输出推送到 `task-logs-topic` (或在结束时一次性上报)。
  - 任务结束后，向 `task-status-update-topic` 发送最终状态 `SUCCESS` 或 `FAILED`，并附带结果。

---

## 分阶段开发计划 (Roadmap)

### 第一阶段：MVP - 核心执行与状态管理

- **目标**: 跑通单个 Agent 执行任务并将状态同步到 Redis 的核心流程。
- **技术栈**: `Java/Python`, `Redis`
- **任务**:
  1. **[Agent]** 开发 Agent 基础框架，能连接 Redis。
  2. **[Agent]** 实现一个简单的 `ShellTaskExecutor`。
  3. **[Redis]** 定义任务信息的 Hash 结构。
  4. **[Agent]** 实现 Agent 从 Redis 的一个 List (作为简易队列) 中拉取任务 (`BRPOP`) 并执行。
  5. **[Agent]** 将任务的 `RUNNING`, `SUCCESS`/`FAILED` 状态回写到 Redis Hash 中。
  6. **[测试]** 编写一个简单的 Redis 客户端脚本，手动 `LPUSH` 一个任务到队列中，验证整个流程。

### 第二阶段：引入分布式协调

- **目标**: 实现多个 Agent 的自动注册与发现，以及 Scheduler 的领导选举。
- **技术栈**: `ZooKeeper`
- **任务**:
  1. **[Agent]** 集成 ZooKeeper 客户端，在启动时注册临时节点到 `/live-agents`。
  2. **[Scheduler]** 开发 Scheduler 基础框架。
  3. **[Scheduler]** 实现领导选举逻辑，确保只有一个 Scheduler 实例是活跃的。
  4. **[Scheduler]** 监听 `/live-agents`，动态获取可用的 Agent 列表。
  5. **[Scheduler]** 修改任务提交流程：任务不再直接进入 Agent 队列，而是先存入一个“待调度”队列 (Redis List)。
  6. **[Scheduler]** 实现一个简单的随机调度策略，从“待调度”队列取出任务，选择一个 Agent，并将任务放入该 Agent
     的专属任务队列 (e.g., `agent:{agent-id}:tasks`)。

### 第三阶段：解耦与异步化

- **目标**: 使用 Kafka 替换 Redis List 作为任务和状态的通信总线，实现系统解耦。
- **技术栈**: `Kafka`
- **任务**:
  1. **[Scheduler]** 调度成功后，不再写入 Redis List，而是将任务消息发送到 `task-dispatch-topic`。
  2. **[Agent]** 改造为 Kafka Consumer，订阅 `task-dispatch-topic` 来获取任务。
  3. **[Agent]** 任务状态更新（执行中/完成）时，不再直接写 Redis，而是将状态消息发送到 `task-status-update-topic`。
  4. **[Manager]** 开发一个独立的 Manager 服务 (或在 Scheduler 的非 Leader 实例中)，订阅 `task-status-update-topic`
     ，负责将状态更新到 Redis 中。
  5. **[Agent]** 实现心跳机制，定期向 `agent-heartbeat-topic` 发送心跳。

### 第四阶段：服务化与对外开放

- **目标**: 建立正式的 API 接入层，并使用 Dubbo 进行内部服务治理。
- **技术栈**: `Spring Boot`, `Dubbo`
- **任务**:
  1. **[API]** 创建 `task-api` 模块，使用 Spring Boot 暴露 RESTful 接口。
  2. **[RPC]** 定义 Dubbo 的 `TaskService` 接口。
  3. **[API]** `task-api` 模块作为 Dubbo Consumer 调用 `TaskService`。
  4. **[Manager/Scheduler]** 将核心的任务管理和调度逻辑封装为 `TaskService` 的 Dubbo Provider 实现。
  5. **[All]** 将 ZK 同时用作 Dubbo 的注册中心。

### 第五阶段：健壮性与可观测性

- **目标**: 增加系统的监控、日志、失败恢复等高级特性。
- **任务**:
  1. **[Observability]** 引入统一的日志框架 (如 SLF4J + Logback)，并将日志聚合到 ELK/Loki 等系统。
  2. **[Failover]** 完善 Scheduler 的失败转移逻辑，当 Agent 心跳超时后，能将其任务重新调度。
  3. **[Monitoring]** 引入 Prometheus + Grafana，暴露关键指标 (如任务总数、成功率、队列长度、Agent 数量)。
  4. **[UI]** (可选) 开发一个简单的前端管理界面，用于查看任务列表、状态和手动提交任务。
