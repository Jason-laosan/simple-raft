package com.raft.core;

import java.io.Serializable;

/**
 * Raft日志条目
 *
 * 日志条目是Raft中的基本数据单元，每个条目包含：
 * - 任期号：创建该条目时Leader的任期
 * - 索引：该条目在日志中的位置
 * - 命令：状态机要执行的命令
 *
 * 日志条目的索引从1开始
 */
public class LogEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 该日志条目创建时的任期号
     * 用于检测日志不一致的情况
     */
    private long term;

    /**
     * 该日志条目在日志中的索引位置
     * 索引从1开始
     */
    private long index;

    /**
     * 要应用到状态机的命令
     * 可以是任意的业务命令，例如：SET key value, DELETE key等
     */
    private String command;

    public LogEntry() {
    }

    public LogEntry(long term, long index, String command) {
        this.term = term;
        this.index = index;
        this.command = command;
    }

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "term=" + term +
                ", index=" + index +
                ", command='" + command + '\'' +
                '}';
    }
}
