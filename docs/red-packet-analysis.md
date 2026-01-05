# 红包系统设计分析

基于 `docs/red-packet-design.md` 中的方案，以下从架构、数据建模、性能可扩展性及风险控制等维度进行分析，并提出后续优化建议。

## 1. 架构评估
| 维度 | 亮点 | 改进建议 |
| --- | --- | --- |
| 模块划分 | RedPacket Service、Grab Service、Recycle Job 职责清晰，结合 MQ 完成异步解耦。 | 考虑拆分 Account Service 接口，定义冻结/解冻/退款 API 契约，便于对接资金系统。 |
| 流程 | 使用 Redis Lua 保障抢红包原子性，过期通过 TTL/延时队列统一回收。 | 建议引入全链路 Trace（如 SkyWalking/Zipkin）监测发红包→抢→回收的时延及失败点。 |
| 可靠性 | 本地消息表 + MQ 保证资金扣款与红包状态一致。 | 增加死信队列与补偿任务，防止极端情况下的消息丢失。 |

## 2. 数据模型评估
1. `red_packet` 主表字段覆盖金额、状态、过期时间，可满足查询需求，采用雪花 ID 方便分库分表。
2. `red_packet_portion` & `red_packet_grab` 明确了拆分份额与抢红包明细，唯一索引 `(packet_id, user_id)` 能保障幂等。
3. 推荐增加以下字段：
   - `red_packet`：`version`（乐观锁，确保并发更新 remain_amount 时一致）；
   - `red_packet_grab`：`client_ip` 或 `channel`，便于审计；
   - `outbox_event`：`event_type`、`retry_count`。

## 3. Redis & 缓存策略
- `rp:{packetId}:pool` 采用 List/Lua，结合 hash tag 可避免跨槽操作，符合高并发设计。
- 建议在 `rp:{packetId}:state` 中记录 `remainAmount`、`status`，方便热点查询，减少 DB 压力。
- Redis 与 DB 之间需有最终一致性校验：定时脚本比对 `remain_amount` 与缓存 `pool` 长度/金额，防止异常。

## 4. 性能与高可用
1. **TPS 1 万**：依赖水平扩展 + Redis 原子脚本即可满足；仍需通过压测验证 Lua 脚本耗时 < 1ms。
2. **限流熔断**：设计提及网关限流与熔断，可结合 Sentinel 在单红包/单用户维度配置阈值，防止恶意请求。
3. **监控指标**：建议重点观测
   - 抢红包成功率、Redis 命中率、Lua 超时数；
   - 回收金额与实际退款金额对账；
   - MQ 堵塞（Lag）与重试次数。

## 5. 风险与改进点
- **资金安全**：需确保资金系统支持冻结→解冻流程，建议对关键链路（扣款、退款）做补偿任务。
- **延时任务可靠性**：Redis 过期事件可能丢失，最好配合 MQ 延时消息或定时扫描双重保障。
- **幂等与重试**：Grab Service 中已有 Redis + DB 唯一索引，落库失败时应将事件写入补偿队列。
- **脚本维护**：Lua 版本升级或 Redis 集群迁移时，需要完整脚本回归测试。

## 6. 代码落地建议
1. 在 `src/main/java/com/raft/redpacket` 目录继续扩展 Repository/Cache 的具体实现（如 MyBatis Mapper、RedisTemplate 封装），保持与文档一致。
2. 引入 Spring Boot 配置（数据源、Redis、MQ）和自动化测试，验证拼手气拆分算法、Lua 抢红包流程、回收任务。
3. 编写压测脚本（JMeter/Locust），重点模拟 500 人高并发抢同一红包，验证限流与幂等逻辑。

> 该分析文档可与原设计文档搭配使用：设计文档提供总体方案，本文聚焦评估与落地建议，便于团队在研发阶段对齐目标与风险。
