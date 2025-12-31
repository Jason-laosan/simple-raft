package com.raft.rpc;

import com.raft.core.LogEntry;

import java.io.Serializable;
import java.util.List;

/**
 * 追加日志RPC请求
 *
 * Leader发送给Follower，用于：
 * 1. 复制日志条目
 * 2. 发送心跳（entries为空）
 *
 * 参数说明：
 * - term: Leader的任期号
 * - leaderId: Leader的节点ID，便于Follower重定向客户端请求
 * - prevLogIndex: 新日志条目之前的日志索引
 * - prevLogTerm: prevLogIndex处的日志任期号
 * - entries: 要存储的日志条目（心跳时为空）
 * - leaderCommit: Leader的已提交日志索引
 */
public class AppendEntriesRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Leader的当前任期号
     */
    private long term;

    /**
     * Leader的节点ID
     * Follower可以用此信息重定向客户端请求
     */
    private String leaderId;

    /**
     * 紧邻新日志条目之前的日志索引
     * 用于一致性检查
     */
    private long prevLogIndex;

    /**
     * prevLogIndex处的日志条目的任期号
     * 用于一致性检查
     */
    private long prevLogTerm;

    /**
     * 要存储的日志条目列表
     * 心跳时为空；为提高效率可能发送多条
     */
    private List<LogEntry> entries;

    /**
     * Leader已提交的最高日志索引
     * Follower根据此值更新自己的commitIndex
     */
    private long leaderCommit;

    public AppendEntriesRequest() {
    }

    public AppendEntriesRequest(long term, String leaderId, long prevLogIndex,
                                long prevLogTerm, List<LogEntry> entries, long leaderCommit) {
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries;
        this.leaderCommit = leaderCommit;
    }

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(String leaderId) {
        this.leaderId = leaderId;
    }

    public long getPrevLogIndex() {
        return prevLogIndex;
    }

    public void setPrevLogIndex(long prevLogIndex) {
        this.prevLogIndex = prevLogIndex;
    }

    public long getPrevLogTerm() {
        return prevLogTerm;
    }

    public void setPrevLogTerm(long prevLogTerm) {
        this.prevLogTerm = prevLogTerm;
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<LogEntry> entries) {
        this.entries = entries;
    }

    public long getLeaderCommit() {
        return leaderCommit;
    }

    public void setLeaderCommit(long leaderCommit) {
        this.leaderCommit = leaderCommit;
    }

    @Override
    public String toString() {
        return "AppendEntriesRequest{" +
                "term=" + term +
                ", leaderId='" + leaderId + '\'' +
                ", prevLogIndex=" + prevLogIndex +
                ", prevLogTerm=" + prevLogTerm +
                ", entries=" + (entries != null ? entries.size() : 0) + " entries" +
                ", leaderCommit=" + leaderCommit +
                '}';
    }
}
