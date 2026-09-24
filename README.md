# threadpool-monitor 线程池监控中心

拉取第三方系统线程池运行指标，图形化展示、历史留存与容量分析。业务系统只需暴露一个只读 HTTP 接口即可接入，无需埋点、无需引入 SDK。

## 功能特性

- **监控大屏**：多数据源 Tab 切换，线程池实时卡片（活跃线程、队列堆积、完成数），ECharts 历史趋势曲线
- **健康等级**：GOOD / OK / WARN / CRIT / IDLE / NO_DATA 六级评定，概览条汇总"需关注"池
- **容量分析**：基于 Little's Law 的任务画像（CPU 密集 / IO 密集 / 混合型 / 空闲 / 无法判定），识别突发型负载，给出调优建议
- **数据源管理**：增删改查、连通性测试、手动拉取，每个数据源独立配置拉取频率
- **鉴权支持**：无鉴权 / Bearer Token / Basic 认证 / 自定义 Header，密钥 AES-GCM 加密落库
- **数据维护**：按保留天数自动清理（默认每日 03:30），页面手动清理（按数据源、保留范围选择、二次确认、分批删除）

## 项目结构

```
threadpool-monitor
├── monitor-server      # 监控中心服务端：拉取调度、快照存储、容量分析、前端大屏
└── demo-provider       # 模拟第三方数据源：演示线程池 + 参考接入组件
```

| 模块 | 端口 | 说明 |
|---|---|---|
| monitor-server | 18080 | 监控中心，Web 界面与所有 API |
| demo-provider | 18081 | 演示数据源 A（无鉴权），含 3 个演示线程池 |
| demo-provider | 18082（可选） | 演示数据源 B（Basic 鉴权，联调用） |

## 快速开始

环境要求：JDK 1.8+、Maven 3.6+

```bash
# 1. 构建
mvn clean package -DskipTests

# 2. 启动监控中心（端口 18080）
java -jar monitor-server/target/monitor-server-1.0.0.jar

# 3. 启动演示数据源（端口 18081）
java -jar demo-provider/target/demo-provider-1.0.0.jar
```

浏览器访问：

- 监控大屏：http://localhost:18080
- 数据源管理：http://localhost:18080/datasources.html

首次使用：进入「数据源管理」→「新增数据源」，基础地址填 `http://localhost:18081`，鉴权选"无"，保存即可看到演示线程池的数据流入。

### 可选：体验鉴权与告警

```bash
# 第二个演示实例，开启 Basic 鉴权（账号 demo / 密码 demo123）
java -jar demo-provider/target/demo-provider-1.0.0.jar --server.port=18082 --demo.auth.enabled=true

# 数据源管理页新增：基础地址 http://localhost:18082，鉴权选 Basic，填入账号密码

# 打开 burst 模式，模拟突发负载，触发队列堆积告警
curl -X POST http://localhost:18081/api/demo/burst/on
```

演示实例内置 3 个参数各异的线程池（orderExecutor / reportExecutor / alarmExecutor），其中 alarmExecutor 队列容量仅 50，burst 模式下容易触发高水位告警。

## 接入业务系统

业务系统（被监控方）只需暴露一个符合契约的只读接口。`demo-provider` 中的 [ThreadPoolMetricsReporter](demo-provider/src/main/java/io/itbob/threadpool/provider/reporter/ThreadPoolMetricsReporter.java) 就是参考接入组件，**整体复制两个类到业务工程即可**：

1. 复制 `demo-provider/src/main/java/io/itbob/threadpool/provider/reporter/` 下的 `ThreadPoolMetricsReporter.java` 和 `MetricsResponse.java`
2. 无需新增任何依赖（仅用到业务工程已有的 spring-web、slf4j）
3. 组件自动遍历 Spring 容器中所有 `ThreadPoolTaskExecutor` Bean，暴露 `GET /api/threadpool/metrics`
4. 应用名取 `spring.application.name`；如需关闭接口，配置 `threadpool.reporter.enabled=false`

### 数据接口契约

```
GET /api/threadpool/metrics
→ 200 + {"code":0,"message":"ok","data":{...}}
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "appName": "your-app",
    "instanceIp": "192.168.1.10",
    "cpuCores": 12,
    "timestamp": 1790212403164,
    "threadPools": [
      {
        "poolName": "orderExecutor",
        "corePoolSize": 4,
        "poolSize": 8,
        "activeCount": 6,
        "maxPoolSize": 8,
        "queueSize": 120,
        "queueRemainingCapacity": 80,
        "queueCapacity": 200,
        "completedTaskCount": 15888
      }
    ]
  }
}
```

| 字段 | 说明 |
|---|---|
| appName | 应用标识，同应用多实例部署时用于区分 |
| instanceIp | 实例 IP |
| cpuCores | 机器逻辑核数，容量分析用（可缺省，旧版本兼容） |
| timestamp | 采集时刻毫秒时间戳（可缺省，服务端回退用接收时间） |
| threadPools[] | 各线程池快照，队列总容量 = queueSize + queueRemainingCapacity |

> 接口为只读且不含敏感业务数据，仍建议生产环境加一层鉴权。demo 工程提供了 [Basic 鉴权过滤器](demo-provider/src/main/java/io/itbob/threadpool/provider/config/AuthFilter.java) 作为参考实现，监控中心侧在「数据源管理」中配置对应的鉴权方式即可，密钥服务端 AES-GCM 加密存储。

## 配置说明（monitor-server）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 18080 | 服务端口 |
| `spring.datasource.url` | `jdbc:h2:file:./data/tpm` | H2 嵌入式文件库，**数据文件位置取决于启动目录** |
| `monitor.retention-days` | 3 | 快照保留天数，每日 03:30 自动清理 |
| `monitor.crypto.key` | 内置默认值 | 鉴权密钥的加密密钥，生产环境用环境变量 `MONITOR_CRYPTO_KEY` 覆盖 |

```bash
# 生产环境示例：自定义加密密钥与数据目录
MONITOR_CRYPTO_KEY=your-key-at-least-16-chars java -jar monitor-server-1.0.0.jar
```

开发调试可加 `--spring.profiles.active=dev`，启用 H2 控制台（`/h2-console`）与 DEBUG 日志。

## 技术栈

- 后端：Spring Boot 2.7.18、JDK 1.8、H2 Database
- 前端：原生 Vue 3 + ECharts（本地 vendor 引入，无前端构建）
- 存储：H2 嵌入式文件库，快照分批清理（每批 2 万行，FETCH FIRST 避免长事务锁表）
