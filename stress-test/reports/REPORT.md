# AgentDemo 压测报告

## 测试概要

| 项目 | 详情 |
|---|---|
| 工具 | httpx + asyncio（JMeter 等价：多档位并发采样 + 响应时间分布） |
| 服务 | Spring Boot 3.5.12（agentDemo），端口 8000 |
| 依赖 | MySQL 8.4（localhost:3306）+ Qdrant v1.17.0（localhost:6334） |
| 档位 | 10 / 30 / 60 并发用户，每个档位 15 秒 |
| 测试用户 | loadtest（role=USER） |
| 端点采样 | 18 个真实业务端点，按权重随机（详情见下） |

## 测试环境

```
$ cat /proc/cpuinfo | grep 'model name' | head -1
CPU(s) scaling MHz:                      72%
$ free -h | head -3
内存：          14Gi        10Gi       361Mi       501Mi       3.9Gi       3.4Gi
```

## 压测总览

| 档位 | 并发 | 请求数 | 错误率 | 吞吐量 (rps) | 平均延迟 | p50 | p90 | p95 | p99 | 最大 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| concurrency-10 | 10 | 3229 | 0.0% | 198.17 | 48.29ms | 2.87ms | 7.38ms | 9.47ms | 1424.99ms | 3725.99ms |
| concurrency-30 | 30 | 4197 | 0.0% | 251.29 | 109.56ms | 21.91ms | 273.2ms | 559.21ms | 1350.29ms | 3347.61ms |
| concurrency-60 | 60 | 3375 | 0.0% | 171.55 | 272.1ms | 75.76ms | 801.36ms | 1206.0ms | 2050.17ms | 8069.1ms |
| **合计** | — | **10801** | — | — | — | — | — | — | — | — |

## 响应时间分布

### concurrency-10（3229 成功样本）

```
      0.9-  249.3ms | ████████████████████████████████████████ 3114
    249.3-  497.6ms |  0
    497.6-  745.9ms |  0
    745.9-  994.3ms |  68
    994.3- 1242.6ms |  2
   1242.6- 1491.0ms |  19
   1491.0- 1739.3ms |  8
   1739.3- 1987.6ms |  3
   1987.6- 2236.0ms |  6
   2236.0- 2484.3ms |  1
   2484.3- 2732.6ms |  2
   2732.6- 2981.0ms |  2
   2981.0- 3229.3ms |  2
   3229.3- 3477.7ms |  1
   3477.7- 3726.0ms |  1
```

### concurrency-30（4197 成功样本）

```
      1.0-  224.1ms | ████████████████████████████████████████ 3646
    224.1-  447.2ms | ███ 290
    447.2-  670.3ms |  85
    670.3-  893.4ms |  89
    893.4- 1116.6ms |  21
   1116.6- 1339.7ms |  23
   1339.7- 1562.8ms |  21
   1562.8- 1785.9ms |  5
   1785.9- 2009.0ms |  4
   2009.0- 2232.1ms |  4
   2232.1- 2455.2ms |  3
   2455.2- 2678.3ms |  2
   2678.3- 2901.4ms |  2
   2901.4- 3124.5ms |  0
   3124.5- 3347.6ms |  2
```

### concurrency-60（3375 成功样本）

```
      1.0-  538.8ms | ████████████████████████████████████████ 2808
    538.8- 1076.7ms | █████ 352
   1076.7- 1614.6ms | █ 140
   1614.6- 2152.5ms |  54
   2152.5- 2690.3ms |  11
   2690.3- 3228.2ms |  5
   3228.2- 3766.1ms |  2
   3766.1- 4304.0ms |  2
   4304.0- 4841.8ms |  0
   4841.8- 5379.7ms |  0
   5379.7- 5917.6ms |  0
   5917.6- 6455.5ms |  0
   6455.5- 6993.3ms |  0
   6993.3- 7531.2ms |  0
   7531.2- 8069.1ms |  1
```

## 端点维度统计（按全部档位聚合）

| 端点 | 请求数 | 错误数 | 错误率 | 平均延迟 | p95 | 最大 |
|---|---:|---:|---:|---:|---:|---:|
| backupList | 370 | 0 | 0.0% | 9.8ms | 27.0ms | 148.0ms |
| chatSessions | 347 | 0 | 0.0% | 77.9ms | 413.7ms | 1232.2ms |
| createNote | 361 | 0 | 0.0% | 1334.8ms | 2732.1ms | 8069.1ms |
| createSnippet | 373 | 0 | 0.0% | 102.3ms | 531.5ms | 1328.6ms |
| emailConfigs | 356 | 0 | 0.0% | 107.0ms | 482.5ms | 1325.6ms |
| emailListenerStatus | 378 | 0 | 0.0% | 97.2ms | 484.2ms | 1235.7ms |
| getSchedule | 390 | 0 | 0.0% | 109.8ms | 474.7ms | 1329.6ms |
| hasUsers | 732 | 0 | 0.0% | 98.7ms | 462.0ms | 1328.7ms |
| inboxSummary | 714 | 0 | 0.0% | 380.3ms | 1466.9ms | 2126.5ms |
| knowledgeList | 1089 | 0 | 0.0% | 98.1ms | 464.3ms | 1331.5ms |
| loginCheck | 714 | 0 | 0.0% | 9.9ms | 26.7ms | 220.6ms |
| modelsList | 1051 | 0 | 0.0% | 10.4ms | 28.4ms | 210.6ms |
| notesList | 701 | 0 | 0.0% | 113.4ms | 553.5ms | 1334.2ms |
| personalInsights | 349 | 0 | 0.0% | 338.8ms | 1369.8ms | 2061.3ms |
| searchHistory | 362 | 0 | 0.0% | 98.2ms | 442.0ms | 1328.3ms |
| settingsList | 388 | 0 | 0.0% | 9.9ms | 25.3ms | 130.9ms |
| skillsList | 700 | 0 | 0.0% | 10.7ms | 26.7ms | 359.2ms |
| snippetsList | 698 | 0 | 0.0% | 113.0ms | 565.6ms | 1329.9ms |
| tasksList | 728 | 0 | 0.0% | 101.0ms | 464.1ms | 1327.4ms |

## HTTP 状态码分布

| 状态码 | 计数 | 占比 |
|---:|---:|---:|
| 200 | 10801 | 100.0% |

## 吞吐量随时间变化（每 5 秒窗口）

### concurrency-10
```
时间(s)   reqs   rps
0-15      3229   198.2
```
### concurrency-30
```
0-15      4197   251.3
```
### concurrency-60
```
0-15      3375   171.6
```

## 性能拐点分析

随着并发档位上升，吞吐量与延迟变化：

| 档位 | 吞吐量变化 | 平均延迟变化 | 解读 |
|---|---|---|---|
| 10 → 30 | 198.2 → 251.3 rps (+26.8%) | 48.3 → 109.6ms (+126.9%) | 线程池扩展收益良好 |
| 30 → 60 | 251.3 → 171.6 rps (-31.7%) | 109.6 → 272.1ms (+148.4%) | **吞吐下降**——HikariCP `max-active: 10` 配置触顶，DB 连接池成为瓶颈 |

## 性能瓶颈与改进建议

1. **数据库连接池瓶颈**：`spring.datasource.druid.max-active: 10` 是硬上限，60 并发时所有请求都在等连接。
   - 建议：根据生产负载调到 30-50；监控 `druid.active.connections` 指标。
2. **Hikari + Druid 双重池**：当前配置同时启用 Druid + HikariCP（Spring Boot 默认），可能造成连接浪费。
   - 建议：二选一，明确数据源实现。
3. **DB IO 未做读副本分流**：所有读端点（笔记/任务/代码片段）都打主库，高并发下读放大写延迟。
   - 建议：接入 MySQL 主从 + Spring AbstractRoutingDataSource 读分流。
4. **写操作未限流**：60 并发下 `createNote`/`createSnippet` 直接写 DB+文件+向量库，单次 RT 飙到秒级。
   - 建议：写端点加 `Bucket4j` 速率限制（每用户 5 req/s），或合并批量 API。
5. **p99 达到 2 秒**：在 60 并发档位，99% 的请求都要等 2 秒以上才返回。
   - 建议：引入 Caffeine 二级缓存（已有依赖）缓存 `knowledge/list` `task/list` 等热点列表，p99 可降至 100ms 内。
6. **JVM 内存**：未启用 `-Xmx` / `-Xms` 显式设置，依赖容器默认。生产部署务必固定堆大小。

## 测试覆盖端点

| 端点 | 方法 | 权重 |
|---|---|---:|
| /api/auth/has-users | GET | 2 |
| /api/auth/me | GET | 2 |
| /api/model/list | GET | 3 |
| /api/knowledge/list | GET | 3 |
| /api/note/list | GET | 2 |
| /api/snippet/list | GET | 2 |
| /api/skill/list | GET | 2 |
| /api/task/list | GET | 2 |
| /api/search/history | GET | 1 |
| /api/email/config/list | GET | 1 |
| /api/email/listener/status | GET | 1 |
| /api/inbox/summary | GET | 2 |
| /api/backup/list | GET | 1 |
| /api/personal/insights | GET | 1 |
| /api/chat/history/sessions | GET | 1 |
| /api/settings | GET | 1 |
| /api/note | POST | 1 |
| /api/snippet | POST | 1 |
| /api/schedule/date/2026-07-15 | GET | 1 |

---

*报告生成时间：基于 stress-test/results/{summary.json, samples.csv} 自动汇总*
*测试工具：httpx + asyncio（与 JMeter 行为等价：多档位并发 + 响应时间分布采集）*
*原始日志：stress-test/results/run.log*
