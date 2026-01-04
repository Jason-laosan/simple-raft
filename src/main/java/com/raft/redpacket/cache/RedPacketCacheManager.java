package com.raft.redpacket.cache;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 抽象 Redis/缓存操作，方便在不同实现间切换。
 */
public interface RedPacketCacheManager {

    /**
     * 将红包份额写入缓存并设置过期时间。
     *
     * @param packetId 红包ID
     * @param portions 拆分份额
     * @param ttl      生存时间
     */
    void cachePortions(long packetId, List<BigDecimal> portions, Duration ttl);

    /**
     * 扣减剩余金额，用于回收逻辑兜底。
     *
     * @param packetId 红包ID
     * @return 剩余金额
     */
    Optional<BigDecimal> getRemainAmount(long packetId);

    /**
     * 删除相关缓存键。
     *
     * @param packetId 红包ID
     */
    void removePacket(long packetId);
}
