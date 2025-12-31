# Simple Raft 实施方案文档

## 1. 项目概述

本项目实现了一个简易的Raft分布式一致性协议，基于Java 8开发，使用Netty进行网络通信，实现了Raft协议的核心功能：领导选举、日志复制和安全性保证。

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Raft Node (节点)                        │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Leader    │  │  Candidate   │  │   Follower   │      │
│  │  Election   │  │              │  │              │      │
│  └─────────────┘  └──────────────┘  └──────────────┘      │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────┐       │
│  │            Log Replication (日志复制)            │       │
│  └─────────────────────────────────────────────────┘       │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐         ┌──────────────┐                │
│  │  RPC Client  │ ◄─────► │  RPC Server  │                │
│  │  (Netty)     │         │  (Netty)     │                │
│  └──────────────┘         └──────────────┘                │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────┐       │
│  │      State Machine (KV存储状态机)                │       │
│  │      SET/DELETE/GET 命令                         │       │
│  └─────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 模块划分

1. **core（核心模块）**
   - `NodeState`：节点状态枚举（Follower/Candidate/Leader）
   - `LogEntry`：日志条目数据结构
   - `RaftNode`：Raft节点核心实现类

2. **rpc（RPC接口模块）**
   - `RequestVoteRequest/Response`：请求投票RPC
   - `AppendEntriesRequest/Response`：追加日志RPC

3. **network（网络通信模块）**
   - `RaftClient`：客户端，发送RPC请求
   - `RaftServer`：服务端，接收RPC请求
   - `RaftMessage`：统一的消息格式

4. **cluster（集群管理模块）**
   - `Peer`：集群节点信息

5. **statemachine（状态机模块）**
   - `StateMachine`：KV存储状态机

6. **config（配置模块）**
   - `RaftConfig`：节点配置类

## 3. 核心实现细节

### 3.1 领导选举机制

**选举流程：**

1. **初始化**
   - 所有节点启动时状态为Follower
   - 启动选举定时器（随机150-300ms）

2. **发起选举**
   - 选举超时后，Follower转为Candidate
   - 增加任期号（currentTerm++）
   - 为自己投票
   - 重置选举定时器
   - 并发向所有节点发送RequestVote RPC

3. **投票规则**
   ```java
   // 拒绝投票的情况：
   // 1. 请求任期号 < 当前任期号
   // 2. 当前任期已投过票，且不是投给该Candidate
   // 3. Candidate的日志不如自己新

   // 同意投票的情况：
   // 1. 请求任期号 >= 当前任期号
   // 2. 未投票或已投票给该Candidate
   // 3. Candidate的日志至少和自己一样新
   ```

4. **成为Leader**
   - 获得多数投票（> N/2）
   - 转为Leader状态
   - 初始化nextIndex和matchIndex
   - 立即发送心跳

**关键代码位置：**
- `RaftNode.startElection()` - src/main/java/com/raft/core/RaftNode.java:191
- `RaftNode.handleRequestVote()` - src/main/java/com/raft/core/RaftNode.java:512
- `RaftNode.becomeLeader()` - src/main/java/com/raft/core/RaftNode.java:262

### 3.2 日志复制机制

**复制流程：**

1. **Leader接收客户端请求**
   ```java
   // 创建新日志条目
   LogEntry entry = new LogEntry(currentTerm, nextIndex, command);
   log.add(entry);
   ```

2. **Leader发送AppendEntries RPC**
   - 定期（50ms）向所有Follower发送
   - 包含日志条目或作为心跳（entries为空）
   - 携带prevLogIndex和prevLogTerm用于一致性检查

3. **Follower处理AppendEntries**
   ```java
   // 一致性检查：
   // 1. 检查prevLogIndex处的日志是否存在
   // 2. 检查prevLogIndex处的日志任期号是否匹配
   //
   // 追加日志：
   // 1. 如果存在冲突条目，删除该条目及之后的所有条目
   // 2. 追加新条目
   // 3. 更新commitIndex
   ```

4. **Leader更新commitIndex**
   - 统计多数节点已复制的索引
   - 更新commitIndex为多数节点的中位数
   - 应用已提交的日志到状态机

**关键代码位置：**
- `RaftNode.appendEntry()` - src/main/java/com/raft/core/RaftNode.java:628
- `RaftNode.replicateLog()` - src/main/java/com/raft/core/RaftNode.java:338
- `RaftNode.handleAppendEntries()` - src/main/java/com/raft/core/RaftNode.java:583
- `RaftNode.updateCommitIndex()` - src/main/java/com/raft/core/RaftNode.java:429

### 3.3 安全性保证

**1. 选举限制**
- 日志比较规则：任期号大的更新，任期号相同则索引大的更新
- 保证新Leader包含所有已提交的日志

**2. 日志匹配性**
- 通过prevLogIndex和prevLogTerm进行一致性检查
- 保证日志的前缀匹配

**3. 提交限制**
- Leader只能提交当前任期的日志
- 通过提交当前任期的日志，间接提交之前任期的日志

**关键代码位置：**
- `RaftNode.isLogUpToDate()` - src/main/java/com/raft/core/RaftNode.java:557

### 3.4 网络通信实现

**基于Netty的通信架构：**

1. **消息格式**
   ```
   [长度字段(4字节)][requestId:JSON消息]
   ```

2. **编解码器链**
   ```
   LengthFieldBasedFrameDecoder  // 解决粘包/拆包
   LengthFieldPrepender          // 添加长度字段
   StringDecoder/Encoder         // 字符串编解码
   RaftClientHandler/ServerHandler // 业务处理
   ```

3. **请求-响应模式**
   - 使用requestId关联请求和响应
   - 使用CompletableFuture实现异步转同步
   - 支持超时机制（3秒）

**关键代码位置：**
- `RaftClient` - src/main/java/com/raft/network/RaftClient.java:1
- `RaftServer` - src/main/java/com/raft/network/RaftServer.java:1

### 3.5 状态机实现

**KV存储状态机：**

```java
// 支持的命令：
SET key value    // 设置键值对
DELETE key       // 删除键
GET key          // 获取值（只读，不通过Raft）

// 状态存储：
ConcurrentHashMap<String, String> data
```

**应用日志：**
- 按日志顺序应用命令
- 保证确定性（相同命令序列产生相同状态）

**关键代码位置：**
- `StateMachine.apply()` - src/main/java/com/raft/statemachine/StateMachine.java:32

## 4. 时序图

### 4.1 领导选举时序图

```
Follower1     Follower2     Follower3
    |             |             |
    | 选举超时    |             |
    |------------>|             |
    | 转为Candidate             |
    | currentTerm++             |
    | 为自己投票                 |
    |                           |
    |--RequestVote-->|          |
    |--RequestVote------------->|
    |                |          |
    |<--投票同意-----|          |
    |<--投票同意----------------|
    |                           |
    | 获得多数投票              |
    | 成为Leader                |
    |                           |
    |--心跳--------->|          |
    |--心跳--------------------->|
```

### 4.2 日志复制时序图

```
Client    Leader      Follower1   Follower2
  |         |             |           |
  |--SET--->|             |           |
  |         | 追加日志    |           |
  |         |             |           |
  |         |--AppendEntries-->|     |
  |         |--AppendEntries-------->|
  |         |             |           |
  |         |<--Success---|           |
  |         |<--Success--------------|
  |         |                         |
  |         | 更新commitIndex        |
  |         | 应用到状态机            |
  |         |                         |
  |<--OK----|                         |
  |         |--心跳(新commitIndex)->  |
  |         |--心跳(新commitIndex)--->|
  |         |             |           |
  |         |      应用到状态机       |
```

## 5. 配置说明

### 5.1 时间参数

```java
// 选举超时时间（毫秒）
ELECTION_TIMEOUT_MIN = 150    // 最小值
ELECTION_TIMEOUT_MAX = 300    // 最大值（随机化避免冲突）

// 心跳间隔（毫秒）
HEARTBEAT_INTERVAL = 50       // 通常为选举超时的1/10

// RPC超时时间（毫秒）
REQUEST_TIMEOUT = 3000        // 请求超时
```

### 5.2 网络参数

```java
// Netty配置
TCP_NODELAY = true            // 禁用Nagle算法
SO_KEEPALIVE = true           // 启用TCP KeepAlive
CONNECT_TIMEOUT = 5000        // 连接超时5秒
```

## 6. 部署指南

### 6.1 编译打包

```bash
cd simple-raft
mvn clean package
```

### 6.2 启动集群

**3节点集群示例：**

```bash
# 节点1
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar \
  localhost 8001 localhost:8002,localhost:8003

# 节点2
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar \
  localhost 8002 localhost:8001,localhost:8003

# 节点3
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar \
  localhost 8003 localhost:8001,localhost:8002
```

### 6.3 生产环境考虑

**当前实现的局限性：**

1. **无持久化**：数据只在内存中，重启后丢失
2. **无日志压缩**：日志会无限增长
3. **无成员变更**：集群成员固定
4. **性能未优化**：单线程处理，未批量化

**改进方向：**

1. **添加持久化**
   - 使用RocksDB或其他KV数据库存储日志
   - 实现Snapshot机制

2. **日志压缩**
   - 实现日志快照
   - 定期压缩旧日志

3. **成员变更**
   - 实现联合一致性（Joint Consensus）
   - 支持动态添加/删除节点

4. **性能优化**
   - 批量发送日志
   - Pipeline复制
   - 并行应用日志到状态机

## 7. 测试用例

### 7.1 单元测试

- 节点初始化测试
- 日志条目创建测试
- 状态机操作测试
- Peer对象测试

**运行测试：**
```bash
mvn test
```

### 7.2 集成测试场景

**场景1：正常选举**
1. 启动3个节点
2. 观察选举过程
3. 验证有且仅有一个Leader

**场景2：日志复制**
1. 在Leader执行SET命令
2. 在所有节点查询数据
3. 验证数据一致

**场景3：Leader故障**
1. 关闭Leader节点
2. 观察重新选举
3. 验证服务可用

**场景4：网络分区**
1. 隔离少数节点
2. 在多数节点继续写入
3. 恢复网络，验证数据同步

## 8. 监控和调试

### 8.1 日志

日志文件位置：`logs/raft.log`

**重要日志事件：**
- 节点状态转换
- 选举发起和结果
- 日志复制成功/失败
- commitIndex更新
- 状态机应用

### 8.2 命令行工具

```bash
status    # 查看节点状态
log       # 查看日志条目
data      # 查看状态机数据
```

## 9. 常见问题排查

### 9.1 选举失败

**现象：**一直没有选出Leader

**可能原因：**
- 网络不通
- 时钟偏差过大
- 节点数为偶数导致分票

**排查方法：**
- 检查网络连接
- 查看日志中的投票情况
- 验证节点配置

### 9.2 日志不一致

**现象：**不同节点的日志不同

**可能原因：**
- 网络分区
- Leader频繁切换
- 实现bug

**排查方法：**
- 使用`log`命令查看各节点日志
- 检查网络状态
- 查看Leader切换历史

### 9.3 数据丢失

**现象：**写入的数据丢失

**可能原因：**
- 未等待提交就查询
- 节点重启（无持久化）
- 网络分区时写入少数节点

**排查方法：**
- 检查commitIndex
- 验证Leader身份
- 查看日志复制状态

## 10. 参考文献

1. [In Search of an Understandable Consensus Algorithm (Extended Version)](https://raft.github.io/raft.pdf) - Raft论文
2. [Raft Visualization](http://thesecretlivesofdata.com/raft/) - Raft可视化演示
3. [Raft GitHub](https://github.com/hashicorp/raft) - HashiCorp的Raft实现
4. [etcd](https://github.com/etcd-io/etcd) - 使用Raft的生产级系统

## 11. 项目文件清单

```
simple-raft/
├── pom.xml                                      # Maven配置
├── README.md                                    # 项目说明
├── IMPLEMENTATION.md                            # 实施方案（本文档）
├── start-node1.bat/sh                          # 启动脚本
├── start-node2.bat/sh
├── start-node3.bat/sh
└── src/
    ├── main/
    │   ├── java/com/raft/
    │   │   ├── RaftNodeBootstrap.java          # 启动类
    │   │   ├── cluster/
    │   │   │   └── Peer.java                   # 节点信息
    │   │   ├── config/
    │   │   │   └── RaftConfig.java             # 配置类
    │   │   ├── core/
    │   │   │   ├── LogEntry.java               # 日志条目
    │   │   │   ├── NodeState.java              # 节点状态
    │   │   │   └── RaftNode.java               # 核心实现
    │   │   ├── network/
    │   │   │   ├── RaftClient.java             # 客户端
    │   │   │   ├── RaftMessage.java            # 消息格式
    │   │   │   └── RaftServer.java             # 服务端
    │   │   ├── rpc/
    │   │   │   ├── AppendEntriesRequest.java
    │   │   │   ├── AppendEntriesResponse.java
    │   │   │   ├── RequestVoteRequest.java
    │   │   │   └── RequestVoteResponse.java
    │   │   └── statemachine/
    │   │       └── StateMachine.java           # 状态机
    │   └── resources/
    │       └── logback.xml                      # 日志配置
    └── test/
        └── java/com/raft/
            └── RaftNodeTest.java                # 单元测试
```

## 12. 总结

本项目实现了Raft协议的核心功能，代码结构清晰，注释详细，适合学习和理解Raft算法。通过实际运行3节点集群，可以直观地观察到领导选举、日志复制、故障恢复等过程。

虽然这是一个简化的教学实现，但包含了Raft协议的核心要素，可以作为学习分布式一致性算法的良好起点。
