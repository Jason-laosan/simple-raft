package com.raft.network;

import java.io.Serializable;

/**
 * Raft网络消息
 *
 * 统一的消息格式，用于在节点间传输
 *
 * 消息类型：
 * - REQUEST_VOTE: 请求投票
 * - APPEND_ENTRIES: 追加日志/心跳
 */
public class RaftMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 消息类型
     */
    private MessageType type;

    /**
     * 消息体（可能是请求或响应）
     */
    private Object body;

    public RaftMessage() {
    }

    public RaftMessage(MessageType type, Object body) {
        this.type = type;
        this.body = body;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        /**
         * 请求投票请求
         */
        REQUEST_VOTE_REQ,

        /**
         * 请求投票响应
         */
        REQUEST_VOTE_RESP,

        /**
         * 追加日志请求
         */
        APPEND_ENTRIES_REQ,

        /**
         * 追加日志响应
         */
        APPEND_ENTRIES_RESP
    }

    @Override
    public String toString() {
        return "RaftMessage{" +
                "type=" + type +
                ", body=" + body +
                '}';
    }
}
