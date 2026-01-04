package com.raft.redpacket.exception;

/**
 * 红包业务异常。
 */
public class RedPacketException extends RuntimeException {

    public RedPacketException(String message) {
        super(message);
    }

    public RedPacketException(String message, Throwable cause) {
        super(message, cause);
    }
}
