# 分布式任务调度系统 - Kubernetes 分层部署指南

## 1. 引言

在微服务架构中，每个服务通常会打包成一个包含所有依赖的“胖 JAR”（Fat JAR）或可执行 JAR。虽然这种方式简化了服务的运行，但当部署到 Kubernetes 等容器平台时，它可能导致以下问题：

*   **Docker 镜像体积庞大**：每个服务都包含大量相同的第三方依赖（如 Spring Framework），导致镜像文件很大。
*   **CI/CD 效率低下**：即使只修改了一行业务代码，也需要重新构建整个庞大的 Docker 镜像，并上传、下载大量未变动的依赖层，拖慢了构建和部署速度。
*   **资源浪费**：镜像仓库和 K8s 节点上存储了大量重复的依赖数据。

为了解决这些问题，我们采用了 **Spring Boot 分层 JAR（Layered JAR）** 结合 **Docker 多阶段构建（Multi-Stage Builds）** 的策略。这种方法能够显著减小 Docker 镜像体积，加速 CI/CD 流程，并更有效地利用缓存。

## 2. 核心理念：分层 JAR 与 Docker 多阶段构建

### 2.1 Spring Boot 分层 JAR

Spring Boot 2.3 引入了分层 JAR 的概念。它允许将可执行 JAR 内部的内容划分为逻辑层。默认情况下，这些层包括：

*   `dependencies`：不常变化的第三方依赖。
*   `spring-boot-loader`：Spring Boot 的类加载器代码。
*   `snapshot-dependencies`：快照依赖，可能变化频繁。
*   `application`：我们自己的业务代码，变化最频繁。

通过在 `bootJar` 任务中启用分层功能，Spring Boot 会在 JAR 包的 `BOOT-INF/layers.idx` 文件中记录这些层的顺序和内容。

### 2.2 Docker 多阶段构建

Docker 多阶段构建允许您在 Dockerfile 中使用多个 `FROM` 语句。每个 `FROM` 语句可以基于不同的基础镜像，并且可以将前一个阶段的构建产物复制到下一个阶段。

我们将结合分层 JAR 的特性，在 Dockerfile 中实现两个阶段：

1.  **构建阶段（Builder Stage）**：使用一个完整的 JDK 环境，复制所有项目代码，运行 Gradle 构建出分层的 JAR，然后利用 `java -Djarmode=layertools -jar app.jar extract` 命令将 JAR 内部的各个层提取到不同的目录。
2.  **运行阶段（Final Stage）**：使用一个轻量级的 JRE 环境作为基础镜像，然后按照依赖层（最不常变动）-> Spring Boot Loader 层 -> 快照依赖层 -> 业务代码层（最常变动）的顺序，将这些提取出来的层复制到最终镜像中。

**其核心优势在于**：当您只修改了业务代码时，Docker 在构建新镜像时会优先使用缓存的 `dependencies` 和 `spring-boot-loader` 层，只需要重新构建和复制变化最小的 `application` 层。这大大减少了构建时间和上传/下载镜像所需的带宽。

## 3. 部署步骤

### 第一步：配置 Gradle 启用分层 JAR

在项目的根 `build.gradle` 文件中，我们已经为所有 Spring Boot 模块启用了分层功能。核心配置如下：

```gradle
// ... (plugins, allprojects, subprojects 等配置)

subprojects {
    // ... (其他通用配置)

    tasks.named('bootJar') {
        layered {
            enabled = true // 启用分层功能
        }
    }
}

// ... (project(':task-common'), project(':task-api') 等模块的特定配置)
```
**说明**：这个配置已经集成到我们当前的 `build.gradle` 中，无需额外手动修改子模块的 `build.gradle`。

### 第二步：为每个微服务编写 Dockerfile

为 `task-api`, `task-manager`, `task-scheduler`, `task-agent-java` 这四个 Java 微服务编写一个统一的多阶段 Dockerfile 模板。`task-agent-python` 将有自己的 Python Dockerfile。

以 `task-api` 为例，在其根目录下创建 `Dockerfile`：

```dockerfile
# ==============================================================================
# 第一阶段: Builder - 构建并提取分层 JAR
# ==============================================================================
# 使用包含完整 JDK 和 Gradle 的镜像作为构建环境
# 请确保 Gradle 版本与项目兼容，JDK 版本与项目源代码编译版本一致 (此处为 Java 8)
FROM gradle:8.5.0-jdk8 AS builder

# 设置 Dockerfile 的参数，用于指定要构建的模块名称
# 在 docker build 命令中使用 --build-arg MODULE_NAME=task-api 来传入
ARG MODULE_NAME

# 定义工作目录
WORKDIR /app

# 复制整个项目代码到构建容器
# 注意：这需要确保 Docker build context 包含所有模块
COPY . /app

# 运行 Gradle 构建指定模块的 bootJar
# --no-daemon: 避免在构建环境中启动 Gradle daemon
# bootJar: 构建 Spring Boot 可执行 JAR
# -x test: 跳过测试，加速构建（CI/CD流水线中通常单独进行测试）
RUN ./gradlew ${MODULE_NAME}:bootJar --no-daemon -x test

# 创建目录用于提取分层内容
RUN mkdir -p build/extracted

# 切换到指定模块的构建目录
WORKDIR /app/${MODULE_NAME}

# 使用 Spring Boot 的 layertools 模式提取 JAR 的内容到 build/extracted 目录
# 提取后会在 build/extracted 目录下生成 application, dependencies 等文件夹
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination ../build/extracted

# ==============================================================================
# 第二阶段: Final Image - 创建最终的轻量级运行镜像
# ==============================================================================
# 使用轻量级的 JRE 镜像作为最终运行环境
# eclipse-temurin 是一个常用的、由 Eclipse Adoptium 维护的开放 JRE 运行时
FROM eclipse-temurin:8-jre-jammy

# 定义环境变量，指定 Spring Profiles 等，可根据需要添加
ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 定义工作目录
WORKDIR /app

# 从 builder 阶段按顺序复制提取出的图层
# 顺序非常重要：先复制不常变化的（如依赖），后复制常变化的（如业务代码）
# 这样当业务代码变更时，Docker 可以最大程度地利用缓存，只需要更新最上层
COPY --from=builder /app/build/extracted/dependencies/ ./
COPY --from=builder /app/build/extracted/spring-boot-loader/ ./
COPY --from=builder /app/build/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/build/extracted/application/ ./

# 暴露 Spring Boot 应用默认端口（如果服务对外提供 HTTP 接口）
EXPOSE 8080

# 启动 Spring Boot 应用
# org.springframework.boot.loader.launch.JarLauncher 是 Spring Boot 分层 JAR 的启动器
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

```

**Python 微服务 (`task-agent-python`) 的 Dockerfile 示例：**

```dockerfile
# 使用官方 Python 基础镜像
FROM python:3.9-slim-buster

# 设置工作目录
WORKDIR /app

# 复制 requirements.txt 并安装依赖，利用 Docker 缓存
COPY task-agent-python/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制 Python 业务代码
COPY task-agent-python/ .

# 定义环境变量 (根据需要添加)
ENV PYTHONUNBUFFERED=1

# 启动 Python 应用
CMD ["python", "main.py"]
```

### 第三步：构建与推送 Docker 镜像

在项目根目录下执行以下命令来构建和推送每个微服务的 Docker 镜像：

```bash
# 构建 task-common 的 JAR (虽然它不直接运行，但确保它能构建成功)
./gradlew :task-common:jar

# 构建并推送 Java 微服务镜像
# 示例: 构建 task-api 镜像
docker build --build-arg MODULE_NAME=task-api -t your-registry/task-api:latest -f task-api/Dockerfile .
docker push your-registry/task-api:latest

# 示例: 构建 task-manager 镜像
docker build --build-arg MODULE_NAME=task-manager -t your-registry/task-manager:latest -f task-manager/Dockerfile .
docker push your-registry/task-manager:latest

# ... 对 task-scheduler 和 task-agent-java 重复上述步骤

# 构建并推送 Python 微服务镜像
docker build -t your-registry/task-agent-python:latest -f task-agent-python/Dockerfile .
docker push your-registry/task-agent-python:latest
```
**说明**：`your-registry` 应替换为您的 Docker 镜像仓库地址（如 `docker.io/your_username`, `gcr.io/your_project` 等）。

### 第四步：Kubernetes 部署

每个微服务在 Kubernetes 中通常会部署为以下资源：

1.  **Deployment (部署)**：管理一组相同的 Pod。
    *   定义了要运行的 Docker 镜像、副本数量（`replicas`，实现水平扩展和负载均衡）、资源限制（CPU/内存）、健康检查（`livenessProbe`, `readinessProbe`）等。
    *   例如，`task-api` 可以配置 3 个副本，`task-agent-java` 可以根据任务负载配置 5 到 10 个副本。

2.  **Service (服务)**：为一组 Pod 提供稳定的网络访问。
    *   为 Deployment 中的所有 Pod 提供一个稳定的 DNS 名称和 IP 地址。
    *   对于 `task-api`，可以配置为 `type: LoadBalancer` 或结合 `Ingress` 对外暴露。
    *   对于 `task-manager`, `task-scheduler`, `task-agent-java` 等内部服务，通常配置为 `type: ClusterIP`，仅在集群内部可访问。

3.  **ConfigMap (配置映射)**：存储非敏感的配置数据。
    *   例如，Kafka broker 地址、Redis 连接信息、ZooKeeper 连接字符串等。
    *   Pod 可以通过环境变量或挂载文件的方式获取 ConfigMap 中的数据。

4.  **Secret (密钥)**：存储敏感数据（如数据库密码、API 密钥）。
    *   以加密的方式存储在 K8s 中，并以环境变量或挂载文件的方式安全地注入到 Pod 中。

**部署流程总结**

1.  **基础设施准备**：确保 K8s 集群中已部署或配置好 ZooKeeper, Kafka, Redis 等依赖服务。
2.  **配置 K8s ConfigMap/Secret**：创建包含所有微服务所需配置的 ConfigMap 和敏感数据的 Secret。
3.  **编写 K8s YAML 文件**：为每个微服务模块编写 Deployment 和 Service 的 YAML 定义文件。
4.  **应用 K8s YAML**：使用 `kubectl apply -f your-service-deployment.yaml` 命令将这些资源部署到 K8s 集群中。
5.  **监控与验证**：使用 `kubectl get pods`, `kubectl logs` 等命令监控服务的运行状况。

### 部署流程示意图

```mermaid
graph LR
    subgraph Client
        Browser[用户浏览器]
    end

    subgraph Kubernetes Cluster
        subgraph Ingress Controller
            Ingress[负载均衡 / API 网关]
        end

        subgraph Service Mesh (可选)
            ServiceMesh[Istio / Linkerd]
        end

        subgraph Core Services
            TaskAPI[task-api (Deployment + Service)]
            TaskManager[task-manager (Deployment)]
            TaskScheduler[task-scheduler (Deployment)]
            TaskAgentJava[task-agent-java (Deployment)]
            TaskAgentPython[task-agent-python (Deployment)]
        end

        subgraph Infrastructure Services
            Kafka[Kafka (Operator / Managed Service)]
            Redis[Redis (Operator / Managed Service)]
            Zookeeper[ZooKeeper (Operator / Managed Service)]
            ConfigMap[配置 (ConfigMap)]
            Secret[敏感信息 (Secret)]
        end

        Browser -- HTTP/HTTPS --> Ingress
        Ingress -- HTTP/HTTPS --> TaskAPI
        TaskAPI -- 生产消息 --> Kafka
        Kafka -- 消费消息 --> TaskManager
        Kafka -- 消费消息 --> TaskScheduler
        Kafka -- 消费消息 --> TaskAgentJava
        Kafka -- 消费消息 --> TaskAgentPython
        TaskManager -- 读写 --> Redis
        TaskScheduler -- 读写 --> Redis
        TaskScheduler -- 协调 --> Zookeeper
        TaskAgentJava -- 读写 --> Redis
        TaskAgentJava -- 协调 --> Zookeeper
        TaskAgentPython -- 读写 --> Redis
        TaskAgentPython -- 协调 --> Zookeeper
    end
```

### 优化与注意事项

*   **资源限制 (Resource Limits)**：在 Deployment 中为每个容器定义 CPU 和内存限制，防止资源争抢。
*   **健康检查 (Health Probes)**：配置 `livenessProbe` 和 `readinessProbe`，确保 K8s 能正确判断 Pod 的健康状态，从而进行自动恢复或停止流量注入。
*   **日志收集**：将容器的日志输出到标准输出 (stdout/stderr)，并配置 K8s 的日志收集方案（如 Fluentd, Loki, ELK Stack）。
*   **Metrics 监控**：集成 Prometheus 等监控系统，收集 JVM Metrics 和 Spring Boot Actuator 提供的指标。

通过遵循上述部署指南，您将能够充分利用 Kubernetes 的优势，实现分布式任务调度系统的高可用、可伸缩和高效管理。
