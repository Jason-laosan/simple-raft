package com.raft.rpc;

import java.io.Serializable;

/**
 * 请求投票RPC响应
 *
 * 节点对RequestVoteRequest的响应
 *
 * 返回值说明：
 * - term: 当前任期号，用于Candidate更新自己的任期
 * - voteGranted: 如果Candidate获得投票则为true
 *
 * 投票规则：
 * 1. 如果请求的任期号小于当前任期号，拒绝投票
 * 2. 如果当前任期已经投过票，且不是投给该Candidate，拒绝投票
 * 3. 如果Candidate的日志不如自己新，拒绝投票
 * 4. 否则，同意投票
 */
public class RequestVoteResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 当前任期号
     * Candidate会用这个值更新自己的任期
     */
    private long term;

    /**
     * 是否同意投票
     * true表示同意，false表示拒绝
     */
    private boolean voteGranted;

    public RequestVoteResponse() {
    }

    public RequestVoteResponse(long term, boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public boolean isVoteGranted() {
        return voteGranted;
    }

    public void setVoteGranted(boolean voteGranted) {
        this.voteGranted = voteGranted;
    }

    @Override
    public String toString() {
        return "RequestVoteResponse{" +
                "term=" + term +
                ", voteGranted=" + voteGranted +
                '}';
    }
}
