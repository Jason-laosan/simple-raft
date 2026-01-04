package com.raft.redpacket.service;

import com.raft.redpacket.exception.RedPacketException;
import com.raft.redpacket.repository.RedPacketRepository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 抢红包服务，依赖 Redis Lua 脚本实现原子扣减。
 */
public class GrabService {

    private final RedPacketRepository repository;
    private final RedisScriptExecutor redisScriptExecutor;

    public GrabService(RedPacketRepository repository,
                       RedisScriptExecutor redisScriptExecutor) {
        this.repository = repository;
        this.redisScriptExecutor = redisScriptExecutor;
    }

    /**
     * 抢红包。
     *
     * @param packetId 红包ID
     * @param userId   用户ID
     * @return 抢到金额
     */
    public BigDecimal grab(long packetId, long userId) {
        Optional<BigDecimal> existing = repository.findGrabRecord(packetId, userId);
        if (existing.isPresent()) {
            return existing.get();
        }

        BigDecimal amount = redisScriptExecutor.grab(packetId, userId);
        if (amount == null) {
            throw new RedPacketException("红包已抢完或超出人数限制");
        }
        repository.saveGrabRecord(packetId, userId, amount);
        return amount;
    }

    /**
     * Redis Lua 执行器抽象。
     */
    public interface RedisScriptExecutor {
        BigDecimal grab(long packetId, long userId);
    }
}
