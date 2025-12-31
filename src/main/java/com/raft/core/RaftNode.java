package com.raft.core;

import com.raft.cluster.Peer;
import com.raft.network.RaftClient;
import com.raft.network.RaftServer;
import com.raft.rpc.*;
import com.raft.statemachine.StateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Raft节点核心类
 *
 * 实现Raft一致性协议的核心逻辑，包括：
 * 1. 领导选举（Leader Election）
 * 2. 日志复制（Log Replication）
 * 3. 安全性保证（Safety）
 *
 * 节点状态说明：
 * - 所有节点初始状态为Follower
 * - 选举超时后，Follower转为Candidate并发起选举
 * - 获得多数投票后，Candidate成为Leader
 * - Leader定期发送心跳维持地位
 */
public class RaftNode {
    private static final Logger logger = LoggerFactory.getLogger(RaftNode.class);

    // ========== 持久化状态（所有节点） ==========
    // 需要在响应RPC前持久化到稳定存储

    /**
     * 当前任期号
     * 单调递增
     */
    private volatile long currentTerm = 0;

    /**
     * 当前任期内获得投票的候选人ID
     * 如果没有投票则为null
     */
    private volatile String votedFor = null;

    /**
     * 日志条目列表
     * 索引从1开始
     */
    private final List<LogEntry> log = new CopyOnWriteArrayList<>();

    // ========== 易失性状态（所有节点） ==========

    /**
     * 已提交的最高日志索引
     * 初始为0，单调递增
     */
    private volatile long commitIndex = 0;

    /**
     * 已应用到状态机的最高日志索引
     * 初始为0，单调递增
     */
    private volatile long lastApplied = 0;

    // ========== 易失性状态（Leader特有） ==========
    // 选举后重新初始化

    /**
     * 对于每个节点，记录需要发送给该节点的下一条日志索引
     * 初始化为Leader最后一条日志索引+1
     */
    private final Map<String, Long> nextIndex = new ConcurrentHashMap<>();

    /**
     * 对于每个节点，记录已复制到该节点的最高日志索引
     * 初始化为0，单调递增
     */
    private final Map<String, Long> matchIndex = new ConcurrentHashMap<>();

    // ========== 节点配置和状态 ==========

    /**
     * 当前节点状态：Follower、Candidate或Leader
     */
    private volatile NodeState state = NodeState.FOLLOWER;

    /**
     * 当前节点ID
     */
    private final String nodeId;

    /**
     * 当前Leader的ID
     * 用于客户端重定向
     */
    private volatile String leaderId = null;

    /**
     * 集群中的其他节点
     */
    private final List<Peer> peers;

    /**
     * 状态机
     * 用于应用已提交的日志
     */
    private final StateMachine stateMachine;

    /**
     * 网络通信客户端
     */
    private final RaftClient client;

    /**
     * 网络通信服务端
     */
    private final RaftServer server;

    // ========== 定时器 ==========

    /**
     * 定时任务调度器
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    /**
     * 选举超时任务
     */
    private ScheduledFuture<?> electionTask;

    /**
     * 心跳任务（Leader专用）
     */
    private ScheduledFuture<?> heartbeatTask;

    /**
     * 选举超时时间（毫秒）
     * 随机化为 150-300ms
     */
    private static final int ELECTION_TIMEOUT_MIN = 150;
    private static final int ELECTION_TIMEOUT_MAX = 300;

    /**
     * 心跳间隔（毫秒）
     * 通常为选举超时的1/10
     */
    private static final int HEARTBEAT_INTERVAL = 50;

    /**
     * 随机数生成器
     */
    private final Random random = new Random();

    /**
     * 构造函数
     *
     * @param nodeId 当前节点ID
     * @param host 当前节点主机
     * @param port 当前节点端口
     * @param peers 集群中的其他节点
     */
    public RaftNode(String nodeId, String host, int port, List<Peer> peers) {
        this.nodeId = nodeId;
        this.peers = peers;
        this.stateMachine = new StateMachine();
        this.client = new RaftClient();
        this.server = new RaftServer(host, port, this);

        logger.info("Raft节点初始化: nodeId={}, host={}, port={}, peers={}",
                    nodeId, host, port, peers.size());
    }

    /**
     * 启动Raft节点
     */
    public void start() {
        // 启动网络服务
        server.start();

        // 启动选举定时器
        resetElectionTimer();

        logger.info("Raft节点启动成功: {}", nodeId);
    }

    /**
     * 停止Raft节点
     */
    public void shutdown() {
        scheduler.shutdownNow();
        server.shutdown();
        client.shutdown();
        logger.info("Raft节点已停止: {}", nodeId);
    }

    /**
     * 重置选举定时器
     * 在以下情况下调用：
     * 1. 节点启动
     * 2. 收到有效的心跳
     * 3. 为其他节点投票
     * 4. 开始新的选举
     */
    private void resetElectionTimer() {
        // 取消旧的定时器
        if (electionTask != null) {
            electionTask.cancel(false);
        }

        // 生成随机超时时间
        int timeout = ELECTION_TIMEOUT_MIN + random.nextInt(ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN);

        // 启动新的定时器
        electionTask = scheduler.schedule(() -> {
            // 如果是Leader，不需要选举
            if (state == NodeState.LEADER) {
                return;
            }
            // 发起选举
            startElection();
        }, timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * 发起选举
     *
     * 选举流程：
     * 1. 转换为Candidate状态
     * 2. 增加当前任期号
     * 3. 为自己投票
     * 4. 重置选举定时器
     * 5. 向所有其他节点发送RequestVote RPC
     */
    private void startElection() {
        // 转换为Candidate
        state = NodeState.CANDIDATE;
        currentTerm++;
        votedFor = nodeId;
        leaderId = null;

        logger.info("节点 {} 发起选举，任期 {}", nodeId, currentTerm);

        // 重置选举定时器
        resetElectionTimer();

        // 获取最后一条日志的信息
        long lastLogIndex = log.isEmpty() ? 0 : log.get(log.size() - 1).getIndex();
        long lastLogTerm = log.isEmpty() ? 0 : log.get(log.size() - 1).getTerm();

        // 创建请求投票RPC
        RequestVoteRequest request = new RequestVoteRequest(
                currentTerm, nodeId, lastLogIndex, lastLogTerm);

        // 投票计数器（包括自己的一票）
        AtomicInteger voteCount = new AtomicInteger(1);

        // 并发向所有节点发送请求
        for (Peer peer : peers) {
            scheduler.execute(() -> {
                try {
                    RequestVoteResponse response = client.requestVote(peer, request);
                    handleRequestVoteResponse(response, voteCount);
                } catch (Exception e) {
                    logger.error("向节点 {} 请求投票失败", peer.getNodeId(), e);
                }
            });
        }
    }

    /**
     * 处理RequestVote响应
     *
     * @param response 响应
     * @param voteCount 投票计数器
     */
    private void handleRequestVoteResponse(RequestVoteResponse response, AtomicInteger voteCount) {
        // 如果响应的任期号大于当前任期，转为Follower
        if (response.getTerm() > currentTerm) {
            becomeFollower(response.getTerm());
            return;
        }

        // 如果不再是Candidate，忽略响应
        if (state != NodeState.CANDIDATE) {
            return;
        }

        // 如果获得投票
        if (response.isVoteGranted()) {
            int votes = voteCount.incrementAndGet();
            logger.info("节点 {} 获得投票，当前票数: {}/{}", nodeId, votes, peers.size() + 1);

            // 如果获得多数投票，成为Leader
            int majority = (peers.size() + 1) / 2 + 1;
            if (votes >= majority) {
                becomeLeader();
            }
        }
    }

    /**
     * 成为Leader
     *
     * 1. 转换为Leader状态
     * 2. 初始化nextIndex和matchIndex
     * 3. 启动心跳定时器
     * 4. 立即发送一次心跳
     */
    private void becomeLeader() {
        if (state != NodeState.CANDIDATE) {
            return;
        }

        state = NodeState.LEADER;
        leaderId = nodeId;

        logger.info("节点 {} 成为Leader，任期 {}", nodeId, currentTerm);

        // 初始化nextIndex和matchIndex
        long lastLogIndex = log.isEmpty() ? 0 : log.get(log.size() - 1).getIndex();
        for (Peer peer : peers) {
            nextIndex.put(peer.getNodeId(), lastLogIndex + 1);
            matchIndex.put(peer.getNodeId(), 0L);
        }

        // 取消选举定时器
        if (electionTask != null) {
            electionTask.cancel(false);
        }

        // 启动心跳定时器
        startHeartbeat();

        // 立即发送一次心跳
        sendHeartbeat();
    }

    /**
     * 启动心跳定时器
     */
    private void startHeartbeat() {
        heartbeatTask = scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                0,
                HEARTBEAT_INTERVAL,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 发送心跳
     *
     * 向所有Follower发送AppendEntries RPC（可能包含日志条目）
     */
    private void sendHeartbeat() {
        if (state != NodeState.LEADER) {
            return;
        }

        for (Peer peer : peers) {
            scheduler.execute(() -> replicateLog(peer));
        }
    }

    /**
     * 复制日志到指定节点
     *
     * @param peer 目标节点
     */
    private void replicateLog(Peer peer) {
        long nextIdx = nextIndex.get(peer.getNodeId());
        long prevLogIndex = nextIdx - 1;
        long prevLogTerm = 0;

        // 获取prevLogTerm
        if (prevLogIndex > 0) {
            for (LogEntry entry : log) {
                if (entry.getIndex() == prevLogIndex) {
                    prevLogTerm = entry.getTerm();
                    break;
                }
            }
        }

        // 准备要发送的日志条目
        List<LogEntry> entries = new ArrayList<>();
        for (LogEntry entry : log) {
            if (entry.getIndex() >= nextIdx) {
                entries.add(entry);
            }
        }

        // 创建AppendEntries请求
        AppendEntriesRequest request = new AppendEntriesRequest(
                currentTerm, nodeId, prevLogIndex, prevLogTerm, entries, commitIndex);

        try {
            AppendEntriesResponse response = client.appendEntries(peer, request);
            handleAppendEntriesResponse(peer, request, response);
        } catch (Exception e) {
            logger.error("向节点 {} 复制日志失败", peer.getNodeId(), e);
        }
    }

    /**
     * 处理AppendEntries响应
     *
     * @param peer 目标节点
     * @param request 请求
     * @param response 响应
     */
    private void handleAppendEntriesResponse(Peer peer, AppendEntriesRequest request, AppendEntriesResponse response) {
        // 如果响应的任期号大于当前任期，转为Follower
        if (response.getTerm() > currentTerm) {
            becomeFollower(response.getTerm());
            return;
        }

        // 如果不再是Leader，忽略响应
        if (state != NodeState.LEADER) {
            return;
        }

        if (response.isSuccess()) {
            // 更新matchIndex和nextIndex
            if (!request.getEntries().isEmpty()) {
                long lastIndex = request.getEntries().get(request.getEntries().size() - 1).getIndex();
                matchIndex.put(peer.getNodeId(), lastIndex);
                nextIndex.put(peer.getNodeId(), lastIndex + 1);

                logger.debug("节点 {} 成功复制到 {}，matchIndex={}",
                            peer.getNodeId(), lastIndex, lastIndex);
            }

            // 更新commitIndex
            updateCommitIndex();
        } else {
            // 日志不一致，回退nextIndex
            long next = nextIndex.get(peer.getNodeId());
            nextIndex.put(peer.getNodeId(), Math.max(1, next - 1));
            logger.debug("节点 {} 日志不一致，回退nextIndex至 {}",
                        peer.getNodeId(), nextIndex.get(peer.getNodeId()));
        }
    }

    /**
     * 更新commitIndex
     *
     * 如果存在N > commitIndex，使得：
     * 1. N被多数节点复制
     * 2. log[N].term == currentTerm
     * 则设置commitIndex = N
     */
    private void updateCommitIndex() {
        // 获取所有matchIndex（包括自己）
        List<Long> indices = new ArrayList<>(matchIndex.values());
        long lastLogIndex = log.isEmpty() ? 0 : log.get(log.size() - 1).getIndex();
        indices.add(lastLogIndex);

        // 排序
        Collections.sort(indices, Collections.reverseOrder());

        // 获取中位数（多数节点已复制的索引）
        int majority = (peers.size() + 1) / 2;
        long N = indices.get(majority - 1);

        // 更新commitIndex
        if (N > commitIndex) {
            // 检查log[N].term == currentTerm
            for (LogEntry entry : log) {
                if (entry.getIndex() == N && entry.getTerm() == currentTerm) {
                    commitIndex = N;
                    logger.info("更新commitIndex至 {}", commitIndex);
                    applyLog();
                    break;
                }
            }
        }
    }

    /**
     * 应用已提交的日志到状态机
     */
    private void applyLog() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            for (LogEntry entry : log) {
                if (entry.getIndex() == lastApplied) {
                    stateMachine.apply(entry);
                    logger.info("应用日志到状态机: {}", entry);
                    break;
                }
            }
        }
    }

    /**
     * 转换为Follower
     *
     * @param term 新任期号
     */
    private void becomeFollower(long term) {
        logger.info("节点 {} 转为Follower，任期从 {} 变为 {}", nodeId, currentTerm, term);

        state = NodeState.FOLLOWER;
        currentTerm = term;
        votedFor = null;
        leaderId = null;

        // 取消心跳定时器
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }

        // 重置选举定时器
        resetElectionTimer();
    }

    /**
     * 处理RequestVote RPC
     *
     * @param request 请求
     * @return 响应
     */
    public RequestVoteResponse handleRequestVote(RequestVoteRequest request) {
        logger.info("收到RequestVote请求: {}", request);

        // 如果请求的任期号大于当前任期，更新当前任期并转为Follower
        if (request.getTerm() > currentTerm) {
            becomeFollower(request.getTerm());
        }

        boolean voteGranted = false;

        // 如果请求的任期号小于当前任期，拒绝投票
        if (request.getTerm() < currentTerm) {
            logger.info("拒绝投票给 {}：任期过旧 ({} < {})",
                       request.getCandidateId(), request.getTerm(), currentTerm);
        }
        // 如果当前任期已投票，且不是投给该Candidate，拒绝投票
        else if (votedFor != null && !votedFor.equals(request.getCandidateId())) {
            logger.info("拒绝投票给 {}：已投票给 {}", request.getCandidateId(), votedFor);
        }
        // 检查Candidate的日志是否至少和自己一样新
        else if (!isLogUpToDate(request.getLastLogIndex(), request.getLastLogTerm())) {
            logger.info("拒绝投票给 {}：日志不够新", request.getCandidateId());
        }
        // 同意投票
        else {
            voteGranted = true;
            votedFor = request.getCandidateId();
            resetElectionTimer();
            logger.info("投票给 {}，任期 {}", request.getCandidateId(), currentTerm);
        }

        return new RequestVoteResponse(currentTerm, voteGranted);
    }

    /**
     * 检查Candidate的日志是否至少和自己一样新
     *
     * 比较规则：
     * 1. 如果任期号不同，任期号大的更新
     * 2. 如果任期号相同，日志更长的更新
     *
     * @param lastLogIndex Candidate的最后一条日志索引
     * @param lastLogTerm Candidate的最后一条日志任期号
     * @return true表示Candidate的日志至少和自己一样新
     */
    private boolean isLogUpToDate(long lastLogIndex, long lastLogTerm) {
        long myLastIndex = log.isEmpty() ? 0 : log.get(log.size() - 1).getIndex();
        long myLastTerm = log.isEmpty() ? 0 : log.get(log.size() - 1).getTerm();

        // 任期号更大，则更新
        if (lastLogTerm > myLastTerm) {
            return true;
        }

        // 任期号相同，索引更大或相等，则更新
        if (lastLogTerm == myLastTerm && lastLogIndex >= myLastIndex) {
            return true;
        }

        return false;
    }

    /**
     * 处理AppendEntries RPC
     *
     * @param request 请求
     * @return 响应
     */
    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        logger.debug("收到AppendEntries请求: {}", request);

        // 如果请求的任期号大于当前任期，更新当前任期并转为Follower
        if (request.getTerm() > currentTerm) {
            becomeFollower(request.getTerm());
        }

        // 如果请求的任期号小于当前任期，拒绝
        if (request.getTerm() < currentTerm) {
            return new AppendEntriesResponse(currentTerm, false);
        }

        // 重置选举定时器
        resetElectionTimer();

        // 更新Leader信息
        leaderId = request.getLeaderId();

        // 如果是Candidate，转为Follower
        if (state == NodeState.CANDIDATE) {
            becomeFollower(currentTerm);
        }

        // 一致性检查：检查prevLogIndex处的日志是否匹配
        if (request.getPrevLogIndex() > 0) {
            LogEntry prevLog = getLogEntry(request.getPrevLogIndex());

            // 如果日志不存在或任期号不匹配，返回失败
            if (prevLog == null || prevLog.getTerm() != request.getPrevLogTerm()) {
                long lastIndex = log.isEmpty() ? 0 : log.get(log.size() - 1).getIndex();
                logger.debug("一致性检查失败: prevLogIndex={}, prevLogTerm={}",
                            request.getPrevLogIndex(), request.getPrevLogTerm());
                return new AppendEntriesResponse(currentTerm, false, lastIndex);
            }
        }

        // 追加日志条目
        if (request.getEntries() != null && !request.getEntries().isEmpty()) {
            for (LogEntry entry : request.getEntries()) {
                // 如果存在冲突的条目，删除该条目及其之后的所有条目
                LogEntry existing = getLogEntry(entry.getIndex());
                if (existing != null && existing.getTerm() != entry.getTerm()) {
                    // 删除从该索引开始的所有条目
                    log.removeIf(e -> e.getIndex() >= entry.getIndex());
                }

                // 追加新条目（如果不存在）
                if (getLogEntry(entry.getIndex()) == null) {
                    log.add(entry);
                    logger.info("追加日志: {}", entry);
                }
            }
        }

        // 更新commitIndex
        if (request.getLeaderCommit() > commitIndex) {
            long lastIndex = log.isEmpty() ? 0 : log.get(log.size() - 1).getIndex();
            commitIndex = Math.min(request.getLeaderCommit(), lastIndex);
            applyLog();
        }

        long lastIndex = log.isEmpty() ? 0 : log.get(log.size() - 1).getIndex();
        return new AppendEntriesResponse(currentTerm, true, lastIndex);
    }

    /**
     * 获取指定索引的日志条目
     *
     * @param index 日志索引
     * @return 日志条目，不存在则返回null
     */
    private LogEntry getLogEntry(long index) {
        for (LogEntry entry : log) {
            if (entry.getIndex() == index) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 客户端请求：追加新的日志条目
     *
     * @param command 命令
     * @return 是否成功
     */
    public boolean appendEntry(String command) {
        // 只有Leader可以接收客户端请求
        if (state != NodeState.LEADER) {
            logger.warn("节点 {} 不是Leader，无法处理客户端请求", nodeId);
            return false;
        }

        // 创建新的日志条目
        long nextIndex = log.isEmpty() ? 1 : log.get(log.size() - 1).getIndex() + 1;
        LogEntry entry = new LogEntry(currentTerm, nextIndex, command);
        log.add(entry);

        logger.info("Leader接收客户端请求，追加日志: {}", entry);

        return true;
    }

    // ========== Getters ==========

    public NodeState getState() {
        return state;
    }

    public long getCurrentTerm() {
        return currentTerm;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public List<LogEntry> getLog() {
        return new ArrayList<>(log);
    }

    public long getCommitIndex() {
        return commitIndex;
    }

    public StateMachine getStateMachine() {
        return stateMachine;
    }
}
