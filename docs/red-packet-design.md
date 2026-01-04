# 红包系统架构设计（拼手气 + 固定金额）

## 需求与约束
- 支持**拼手气**（随机金额）与**固定金额**两种类型。
- 单个红包最大接收人数 **500 人**。
- 超时时间 **30 分钟**，到期后需自动回收剩余金额并返还给发起人。
- 峰值吞吐 **10,000 TPS**，要求可水平扩展、低延迟。
- 持久化使用 **MySQL**，热点访问与分布式协同依赖 **Redis**，异步处理采用 **消息队列（Kafka/RabbitMQ 均可）**。

## 架构概览
```
Client
   │
API Gateway ──► Rate Limiter / Auth
   │
   ├─► RedPacket Service  ──► MySQL (RedPacket / Portion / Grab tables)
   │          │
   │          ├─► Redis Cluster （红包池、状态、幂等记录、延时键）
   │          ├─► MQ：事件通知（扣款、抢红包成功、回收）
   │          └─► Cache Consistency / Lua 脚本原子扣减
   │
   ├─► Grab Service ──► Redis Lua（扣减份额 + 人数 + 防重）
   │          │
   │          └─► Async Writer / CQRS 同步至 MySQL
   │
   └─► Recycle Job ──► Redis 过期事件 / ZSet 扫描
               └─► Account Service（退款）
```

### 模块职责
1. **RedPacket Service**：校验请求、拆分金额、写入 MySQL 并构建 Redis 红包池，触发资金冻结/扣款事件。
2. **Grab Service**：执行抢红包 Lua 脚本，保证“人数 ≤500 + 份额原子扣减 + 幂等”。结果异步写库。
3. **Recycle Job**：基于延时队列或 Redis 过期键在 30 分钟触发回收操作，返还剩余金额并关闭红包。
4. **Account Service**：对接资金系统，完成扣款、解冻、退款等操作。
5. **Monitoring & Safeguard**：Prometheus + Grafana 指标；限流、熔断、重试链路。

## 数据模型（MySQL）

| 表名 | 关键字段 | 说明 |
| ---- | ------- | ---- |
| `red_packet` | `packet_id`, `sender_id`, `type`, `total_amount`, `remain_amount`, `status`, `expire_at` | 红包主表，`packet_id` 采用雪花 ID 并进行分库分表。 |
| `red_packet_portion` | `packet_id`, `portion_id`, `amount`, `status` | 固定金额直接写定额，拼手气存拆分后的金额或生成规则。 |
| `red_packet_grab` | `packet_id`, `user_id`, `amount`, `grab_time` | 抢红包结果表，支持唯一索引 `(packet_id, user_id)` 保证幂等。 |
| `outbox_event` | `event_id`, `payload`, `status` | 本地消息表，确保与资金系统 / MQ 的最终一致性。 |

> 推荐进行 8~16 库分片，热数据按 `packet_id` hash；读写分离用于明细查询。

## Redis 结构

| Key | 结构 | 用途 |
| --- | ---- | ---- |
| `rp:{packetId}:pool` | List / Lua 管理 | 存储待抢金额；固定金额使用 List，拼手气用 Lua 生成随机值。 |
| `rp:{packetId}:count` | String | 当前已抢人数，限制 ≤500。 |
| `rp:{packetId}:records` | Hash | 记录用户已抢金额，防止重复抢。 |
| `rp:{packetId}:state` | Hash | 红包状态缓存，判定是否可抢。 |
| `rp:{packetId}:ttl` | Key with TTL / ZSet | 30 分钟后触发过期事件，供回收任务扫描。 |

Redis 使用 Cluster，并对 key 加哈希标签（如 `{packetId}`）保证同一红包落到同一分片，Lua 脚本原子执行扣减逻辑。

## 流程说明
1. **发红包**
   - API 校验参数（金额>0、份数≤500、余额充足）。
   - 使用“二倍均值”或定额策略拆分金额，持久化 `red_packet` + `red_packet_portion`。
   - 向 Account Service 发出扣款事务消息，并将份额写入 Redis `pool`，设置 30 分钟 TTL。

2. **抢红包**
   - 接口先检查 `state`/`count`，再执行 Lua：
     ```
     if count >= 500 then return nil end
     amount = LPOP pool
     if amount then INCR count; HSET records[user]=amount end
     return amount
     ```
   - 成功后写入 `records` 及 MQ 事件（用于异步落库）；失败返回“已抢完/人数超限”。

3. **超时回收**
   - Redis 过期事件或定时任务扫描 `expire_at`。
   - 获取未抢金额 & 状态分布，调用 Account Service 退款，更新 `red_packet` 状态为 `RECYCLED`。
   - 清理 Redis 缓存，发出回收事件供监控。

## 吞吐与高可用
- **水平扩展**：API 层容器化（K8s），无状态服务可轻松扩容；Redis/MQ/MySQL 均采用分片 + 主从。
- **限流熔断**：网关对单用户/单红包限速，接入熔断器（如 Sentinel/Resilience4j）。
- **幂等保障**：Redis Hash + MySQL 唯一索引 + 业务幂等 token。
- **数据一致性**：本地消息表 + MQ（可靠事件）或事务消息（RocketMQ 半消息）保证资金与红包状态一致。
- **监控**：关键指标（创建/抢成功率、Lua 耗时、回收金额、Redis QPS、MQ Lag）接入 Prometheus；异常通过告警渠道推送。

## 关键 Java 代码（位于 `com.raft.redpacket` 包）
- `RedPacketService`：负责创建、拆分、缓存写入。
- `GrabService`：封装 Redis Lua 执行+异步持久化。
- `RedPacketRecycleJob`：扫描过期红包并回收。
- `RedPacketCreateRequest`、`RedPacket`、`RedPacketType`、`RedPacketStatus`：领域模型。
- `RedPacketRepository`、`RedPacketCacheManager`、`RedisScriptExecutor` 等接口：抽象持久化、缓存和脚本执行，便于在不同环境下注入实现。

代码示例可参考 `src/main/java/com/raft/redpacket` 目录，包含核心领域对象与服务逻辑，便于进一步接入 Spring Boot、MyBatis、RedisTemplate 等具体技术栈。
