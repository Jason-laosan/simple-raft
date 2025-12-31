package com.raft.rpc;

import java.io.Serializable;

/**
 * 追加日志RPC响应
 *
 * Follower对AppendEntriesRequest的响应
 *
 * 返回值说明：
 * - term: 当前任期号，用于Leader更新自己的任期
 * - success: 如果Follower包含与prevLogIndex和prevLogTerm匹配的日志，则为true
 *
 * 一致性检查规则：
 * 1. 如果请求的任期号小于当前任期号，返回false
 * 2. 如果日志在prevLogIndex处不存在或任期号不匹配，返回false
 * 3. 如果存在冲突的日志条目，删除该条目及其之后的所有条目
 * 4. 追加日志中不存在的新条目
 * 5. 更新commitIndex
 */
public class AppendEntriesResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 当前任期号
     * Leader会用这个值更新自己的任期
     */
    private long term;

    /**
     * 是否成功追加日志
     * true表示成功，false表示失败（一致性检查未通过）
     */
    private boolean success;

    /**
     * Follower的最后一条日志索引
     * 用于Leader快速定位下一次要发送的日志位置
     */
    private long lastLogIndex;

    public AppendEntriesResponse() {
    }

    public AppendEntriesResponse(long term, boolean success) {
        this.term = term;
        this.success = success;
    }

    public AppendEntriesResponse(long term, boolean success, long lastLogIndex) {
        this.term = term;
        this.success = success;
        this.lastLogIndex = lastLogIndex;
    }

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getLastLogIndex() {
        return lastLogIndex;
    }

    public void setLastLogIndex(long lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
    }

    @Override
    public String toString() {
        return "AppendEntriesResponse{" +
                "term=" + term +
                ", success=" + success +
                ", lastLogIndex=" + lastLogIndex +
                '}';
    }
}
