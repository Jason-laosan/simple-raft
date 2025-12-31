package com.raft.core;

/**
 * Raft节点的三种状态
 *
 * 在Raft协议中，每个节点在任意时刻都处于以下三种状态之一：
 * 1. Follower（跟随者）：被动接收Leader的日志和心跳
 * 2. Candidate（候选人）：发起选举，竞争Leader
 * 3. Leader（领导者）：处理客户端请求，同步日志到其他节点
 */
public enum NodeState {
    /**
     * 跟随者状态
     * - 响应来自Leader和Candidate的RPC
     * - 如果选举超时未收到Leader心跳，则转换为Candidate
     */
    FOLLOWER,

    /**
     * 候选人状态
     * - 发起选举，增加任期号
     * - 为自己投票，并向其他节点请求投票
     * - 如果收到大多数投票，则成为Leader
     * - 如果收到新Leader的心跳，则转换为Follower
     * - 如果选举超时，则开始新一轮选举
     */
    CANDIDATE,

    /**
     * 领导者状态
     * - 接收客户端请求
     * - 向所有Follower发送日志复制请求
     * - 定期发送心跳以维持领导地位
     */
    LEADER
}
