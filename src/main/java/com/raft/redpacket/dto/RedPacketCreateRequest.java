package com.raft.redpacket.dto;

import com.raft.redpacket.model.RedPacketType;

import java.math.BigDecimal;

/**
 * 发红包请求。
 */
public class RedPacketCreateRequest {
    private Long senderId;
    private BigDecimal totalAmount;
    private int count;
    private RedPacketType type;

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
}
