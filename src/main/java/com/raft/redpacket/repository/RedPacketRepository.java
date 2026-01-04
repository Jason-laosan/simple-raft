package com.raft.redpacket.repository;

import com.raft.redpacket.model.RedPacket;
import com.raft.redpacket.model.RedPacketStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 红包持久化接口，背后可以使用 MySQL + MyBatis 实现。
 */
public interface RedPacketRepository {

    /**
     * 持久化红包信息以及拆分后的份额。
     *
     * @param packet      红包
     * @param portions    金额拆分
     */
    void saveRedPacket(RedPacket packet, List<BigDecimal> portions);

    /**
     * 记录抢红包明细。
     *
     * @param packetId 红包ID
     * @param userId   用户ID
     * @param amount   抢到金额
     */
    void saveGrabRecord(long packetId, long userId, BigDecimal amount);

    /**
     * 查询抢红包结果。
     *
     * @param packetId 红包ID
     * @param userId   用户ID
     * @return 抢到金额
     */
    Optional<BigDecimal> findGrabRecord(long packetId, long userId);

    /**
     * 查询到期未关闭的红包。
     *
     * @param deadline 截至时间
     * @return 需要回收的红包ID列表
     */
    List<RedPacket> findExpiredPackets(LocalDateTime deadline);

    /**
     * 更新红包状态。
     *
     * @param packetId 红包ID
     * @param status   新状态
     */
    void updateStatus(long packetId, RedPacketStatus status);

    /**
     * 查询红包详情。
     *
     * @param packetId 红包ID
     * @return 红包
     */
    Optional<RedPacket> findById(long packetId);
}
