# Simple Raft - 简易Raft一致性协议实现

这是一个基于Java的简易Raft分布式一致性协议实现，用于学习和理解Raft算法。

## 功能特性

- **领导选举（Leader Election）**：自动选举Leader，支持Leader故障自动恢复
- **日志复制（Log Replication）**：Leader将日志复制到所有Follower节点
- **安全性保证（Safety）**：保证已提交的日志不会丢失
- **KV存储状态机**：实现了简单的键值存储，支持SET/DELETE/GET操作
- **网络通信**：基于Netty实现高性能网络通信
- **交互式命令行**：提供友好的命令行界面进行操作

## 技术栈

- **Java 8**
- **Netty 4.1** - 网络通信框架
- **FastJSON 2** - JSON序列化
- **SLF4J + Logback** - 日志框架
- **Maven** - 项目构建工具

## 项目结构

```
simple-raft/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/raft/
│   │   │       ├── cluster/          # 集群节点管理
│   │   │       │   └── Peer.java     # 节点信息
│   │   │       ├── config/           # 配置
│   │   │       │   └── RaftConfig.java
│   │   │       ├── core/             # Raft核心实现
│   │   │       │   ├── LogEntry.java      # 日志条目
│   │   │       │   ├── NodeState.java     # 节点状态
│   │   │       │   └── RaftNode.java      # Raft节点核心类
│   │   │       ├── network/          # 网络通信层
│   │   │       │   ├── RaftClient.java    # 客户端
│   │   │       │   ├── RaftMessage.java   # 消息格式
│   │   │       │   └── RaftServer.java    # 服务端
│   │   │       ├── rpc/              # RPC接口
│   │   │       │   ├── AppendEntriesRequest.java
│   │   │       │   ├── AppendEntriesResponse.java
│   │   │       │   ├── RequestVoteRequest.java
│   │   │       │   └── RequestVoteResponse.java
│   │   │       ├── statemachine/     # 状态机
│   │   │       │   └── StateMachine.java
│   │   │       └── RaftNodeBootstrap.java  # 启动类
│   │   └── resources/
│   │       └── logback.xml           # 日志配置
│   └── test/
└── pom.xml
```

## 快速开始

### 1. 编译项目

```bash
cd simple-raft
mvn clean package
```

编译成功后会在 `target` 目录生成 `simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar` 文件。

### 2. 启动3节点集群

打开3个终端窗口，分别启动3个节点：

**节点1 (端口8001):**
```bash
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8001 localhost:8002,localhost:8003
```

**节点2 (端口8002):**
```bash
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8002 localhost:8001,localhost:8003
```

**节点3 (端口8003):**
```bash
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8003 localhost:8001,localhost:8002
```

### 3. 使用命令行操作

启动后，节点会自动进行Leader选举。在任意一个节点的命令行中输入：

```bash
# 查看节点状态
> status

# 设置键值对（只有Leader可以处理写请求）
> set name Alice
> set age 25

# 查询值（所有节点都可以读）
> get name
name = Alice

# 删除键
> delete age

# 查看所有数据
> data

# 查看日志
> log

# 退出
> exit
```

## 使用脚本启动集群（推荐）

### Windows系统

创建3个批处理文件：

**start-node1.bat:**
```batch
@echo off
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8001 localhost:8002,localhost:8003
pause
```

**start-node2.bat:**
```batch
@echo off
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8002 localhost:8001,localhost:8003
pause
```

**start-node3.bat:**
```batch
@echo off
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8003 localhost:8001,localhost:8002
pause
```

双击运行这3个批处理文件即可启动集群。

### Linux/Mac系统

创建3个shell脚本：

**start-node1.sh:**
```bash
#!/bin/bash
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8001 localhost:8002,localhost:8003
```

**start-node2.sh:**
```bash
#!/bin/bash
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8002 localhost:8001,localhost:8003
```

**start-node3.sh:**
```bash
#!/bin/bash
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8003 localhost:8001,localhost:8002
```

添加执行权限并运行：
```bash
chmod +x start-node*.sh
./start-node1.sh &
./start-node2.sh &
./start-node3.sh &
```

## Raft协议说明

### 节点状态

Raft中的每个节点在任意时刻都处于以下三种状态之一：

- **Follower（跟随者）**：被动接收Leader的日志和心跳
- **Candidate（候选人）**：发起选举，竞争Leader
- **Leader（领导者）**：处理客户端请求，同步日志到其他节点

### 领导选举

1. 所有节点初始状态为Follower
2. 如果Follower在选举超时时间内未收到Leader心跳，转为Candidate
3. Candidate增加任期号，为自己投票，并向其他节点请求投票
4. 获得多数投票的Candidate成为Leader
5. Leader定期发送心跳维持地位

### 日志复制

1. 客户端向Leader发送命令
2. Leader将命令追加到本地日志
3. Leader并发向所有Follower发送AppendEntries RPC
4. Follower接收并追加日志，返回成功
5. Leader收到多数节点的成功响应后，提交日志
6. Leader将已提交的日志应用到状态机
7. Leader在下次心跳中通知Follower更新commitIndex
8. Follower应用已提交的日志到状态机

### 安全性保证

- **选举安全性**：一个任期内最多只有一个Leader
- **日志匹配性**：如果两个日志在某个索引处的任期号相同，则它们在该索引及之前的所有条目都相同
- **Leader完整性**：如果某条日志在某个任期被提交，则该日志必然出现在后续任期的Leader中
- **状态机安全性**：如果某个节点已应用某条日志，则其他节点在该索引处不会应用不同的日志

## 测试场景

### 场景1：正常操作
1. 启动3节点集群
2. 观察Leader选举
3. 在Leader节点执行SET/DELETE操作
4. 在所有节点查看数据一致性

### 场景2：Leader故障恢复
1. 启动3节点集群
2. 找到Leader节点（通过status命令）
3. 关闭Leader节点
4. 观察剩余节点重新选举
5. 重启被关闭的节点，观察其重新加入集群

### 场景3：网络分区
1. 启动3节点集群
2. 隔离一个节点（模拟网络分区）
3. 在隔离节点尝试写入，应该失败
4. 在多数节点（2个）继续写入，应该成功
5. 恢复网络，观察数据同步

## 注意事项

1. **生产环境不可用**：这是一个简化的教学实现，缺少以下生产级特性：
   - 持久化存储（当前只在内存中）
   - 日志压缩（Snapshot）
   - 集群成员变更
   - 性能优化

2. **端口占用**：确保指定的端口未被占用

3. **防火墙**：如果在不同机器上部署，确保防火墙允许相应端口通信

4. **日志文件**：日志文件会生成在 `logs/` 目录下

## 常见问题

**Q: 为什么我的写入命令没有生效？**

A: 只有Leader节点可以处理写入请求。使用 `status` 命令查看当前节点是否为Leader，如果不是，可以查看Leader ID并切换到Leader节点操作。

**Q: 如何验证数据一致性？**

A: 在Leader节点写入数据后，在所有节点使用 `data` 命令查看，应该看到相同的数据。

**Q: 节点启动后一直没有选出Leader？**

A: 检查网络连接，确保节点之间可以正常通信。查看日志文件了解详细错误信息。

## 参考资料

- [Raft论文](https://raft.github.io/raft.pdf)
- [Raft可视化](http://thesecretlivesofdata.com/raft/)
- [Raft官网](https://raft.github.io/)

## 许可证

MIT License

## 作者

Simple Raft Implementation - 2025
