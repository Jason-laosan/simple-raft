package com.raft.redpacket.id;

/**
 * ID 生成器抽象，方便替换为雪花算法或数据库序列。
 */
public interface IdGenerator {

    /**
     * 生成全局唯一的红包 ID。
     *
     * @return 唯一 ID
     */
    long nextId();
}
