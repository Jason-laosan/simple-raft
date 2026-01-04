package com.raft.redpacket.model;

/**
 * 红包整体生命周期。
 */
public enum RedPacketStatus {
    CREATED,
    DISTRIBUTING,
    COMPLETED,
    RECYCLED,
    CLOSED
}
