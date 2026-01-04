package com.raft.redpacket.service;

import com.raft.redpacket.cache.RedPacketCacheManager;
import com.raft.redpacket.dto.RedPacketCreateRequest;
import com.raft.redpacket.exception.RedPacketException;
import com.raft.redpacket.id.IdGenerator;
import com.raft.redpacket.model.RedPacket;
import com.raft.redpacket.model.RedPacketStatus;
import com.raft.redpacket.model.RedPacketType;
import com.raft.redpacket.repository.RedPacketRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 红包创建与拆分服务。
 */
public class RedPacketService {

    private static final int MAX_RECIPIENTS = 500;
    private static final Duration TTL = Duration.ofMinutes(30);

    private final RedPacketRepository repository;
    private final RedPacketCacheManager cacheManager;
    private final IdGenerator idGenerator;

    public RedPacketService(RedPacketRepository repository,
                            RedPacketCacheManager cacheManager,
                            IdGenerator idGenerator) {
        this.repository = repository;
        this.cacheManager = cacheManager;
        this.idGenerator = idGenerator;
    }

    /**
     * 创建红包。
     *
     * @param request 请求
     * @return 红包ID
     */
    public long createRedPacket(RedPacketCreateRequest request) {
        validate(request);
        long packetId = idGenerator.nextId();
        List<BigDecimal> portions = splitAmount(request);

        RedPacket packet = new RedPacket();
        packet.setPacketId(packetId);
        packet.setSenderId(request.getSenderId());
        packet.setTotalAmount(request.getTotalAmount());
        packet.setRemainAmount(request.getTotalAmount());
        packet.setCount(request.getCount());
        packet.setType(request.getType());
        packet.setStatus(RedPacketStatus.CREATED);
        packet.setExpireAt(LocalDateTime.now().plus(TTL));
        packet.setCreatedAt(LocalDateTime.now());

        repository.saveRedPacket(packet, portions);
        cacheManager.cachePortions(packetId, portions, TTL);
        return packetId;
    }

    private void validate(RedPacketCreateRequest request) {
        if (request.getSenderId() == null) {
            throw new RedPacketException("senderId required");
        }
        if (request.getTotalAmount() == null
                || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RedPacketException("amount must be positive");
        }
        if (request.getCount() <= 0 || request.getCount() > MAX_RECIPIENTS) {
            throw new RedPacketException("count must be in (0, 500]");
        }
        if (request.getType() == null) {
            throw new RedPacketException("type required");
        }
    }

    private List<BigDecimal> splitAmount(RedPacketCreateRequest request) {
        if (request.getType() == RedPacketType.FIXED) {
            BigDecimal average = request.getTotalAmount()
                    .divide(BigDecimal.valueOf(request.getCount()), 2, RoundingMode.DOWN);
            return Collections.nCopies(request.getCount(), average);
        }
        return luckySplit(request.getTotalAmount(), request.getCount());
    }

    private List<BigDecimal> luckySplit(BigDecimal total, int count) {
        List<BigDecimal> result = new ArrayList<>(count);
        BigDecimal remain = total;
        for (int i = 0; i < count - 1; i++) {
            double max = remain.multiply(BigDecimal.valueOf(2))
                    .divide(BigDecimal.valueOf(count - i), 2, RoundingMode.DOWN)
                    .doubleValue();
            double amount = ThreadLocalRandom.current().nextDouble(0.01, Math.max(0.02, max));
            BigDecimal value = BigDecimal.valueOf(amount).setScale(2, RoundingMode.DOWN);
            result.add(value);
            remain = remain.subtract(value);
        }
        result.add(remain.setScale(2, RoundingMode.DOWN));
        return result;
    }
}
