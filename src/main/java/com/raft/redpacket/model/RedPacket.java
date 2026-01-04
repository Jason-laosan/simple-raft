package com.raft.redpacket.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 红包聚合根。
 */
public class RedPacket {
    private Long packetId;
    private Long senderId;
    private BigDecimal totalAmount;
    private BigDecimal remainAmount;
    private int count;
    private RedPacketType type;
    private RedPacketStatus status;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;

    public Long getPacketId() {
        return packetId;
    }

    public void setPacketId(Long packetId) {
        this.packetId = packetId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getRemainAmount() {
        return remainAmount;
    }

    public void setRemainAmount(BigDecimal remainAmount) {
        this.remainAmount = remainAmount;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public RedPacketType getType() {
        return type;
    }

    public void setType(RedPacketType type) {
        this.type = type;
    }

    public RedPacketStatus getStatus() {
        return status;
    }

    public void setStatus(RedPacketStatus status) {
        this.status = status;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
