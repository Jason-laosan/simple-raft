package com.raft.redpacket;

/**
 * 红包类型：拼手气（随机金额）或固定金额。
 */
public enum RedPacketType {
    /**
     * 拼手气红包，采用随机金额分配。
     */
    LUCKY,
    /**
     * 固定金额红包，所有领取者金额一致。
     */
    FIXED
}
