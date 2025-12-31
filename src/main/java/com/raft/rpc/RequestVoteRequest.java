package com.raft.rpc;

import java.io.Serializable;

/**
 * 请求投票RPC请求
 *
 * Candidate在选举期间发送给其他节点，请求投票
 *
 * 参数说明：
 * - term: Candidate的任期号
 * - candidateId: Candidate的节点ID
 * - lastLogIndex: Candidate最后一条日志的索引
 * - lastLogTerm: Candidate最后一条日志的任期号
 */
public class RequestVoteRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Candidate的当前任期号
     */
    private long term;

    /**
     * 请求投票的Candidate的ID
     */
    private String candidateId;

    /**
     * Candidate最后一条日志条目的索引
     * 用于判断Candidate的日志是否比自己新
     */
    private long lastLogIndex;

    /**
     * Candidate最后一条日志条目的任期号
     * 用于判断Candidate的日志是否比自己新
     */
    private long lastLogTerm;

    public RequestVoteRequest() {
    }

    public RequestVoteRequest(long term, String candidateId, long lastLogIndex, long lastLogTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public long getLastLogIndex() {
        return lastLogIndex;
    }

    public void setLastLogIndex(long lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
    }

    public long getLastLogTerm() {
        return lastLogTerm;
    }

    public void setLastLogTerm(long lastLogTerm) {
        this.lastLogTerm = lastLogTerm;
    }

    @Override
    public String toString() {
        return "RequestVoteRequest{" +
                "term=" + term +
                ", candidateId='" + candidateId + '\'' +
                ", lastLogIndex=" + lastLogIndex +
                ", lastLogTerm=" + lastLogTerm +
                '}';
    }
}
