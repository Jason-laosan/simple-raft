package com.raft.statemachine;

import com.raft.core.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 状态机
 *
 * 实现一个简单的KV存储状态机
 * 支持的命令：
 * - SET key value: 设置键值对
 * - DELETE key: 删除键
 * - GET key: 获取值（只读操作，不通过Raft）
 *
 * 状态机保证：
 * 1. 确定性：相同的命令序列产生相同的状态
 * 2. 顺序性：按日志顺序应用命令
 */
public class StateMachine {
    private static final Logger logger = LoggerFactory.getLogger(StateMachine.class);

    /**
     * KV存储
     */
    private final Map<String, String> data = new ConcurrentHashMap<>();

    /**
     * 应用日志条目到状态机
     *
     * @param entry 日志条目
     */
    public void apply(LogEntry entry) {
        String command = entry.getCommand();
        if (command == null || command.isEmpty()) {
            return;
        }

        try {
            String[] parts = command.split(" ", 3);
            String operation = parts[0].toUpperCase();

            switch (operation) {
                case "SET":
                    if (parts.length >= 3) {
                        String key = parts[1];
                        String value = parts[2];
                        data.put(key, value);
                        logger.info("状态机执行: SET {} = {}", key, value);
                    } else {
                        logger.warn("无效的SET命令: {}", command);
                    }
                    break;

                case "DELETE":
                    if (parts.length >= 2) {
                        String key = parts[1];
                        data.remove(key);
                        logger.info("状态机执行: DELETE {}", key);
                    } else {
                        logger.warn("无效的DELETE命令: {}", command);
                    }
                    break;

                default:
                    logger.warn("未知的命令: {}", command);
                    break;
            }
        } catch (Exception e) {
            logger.error("应用日志到状态机失败: {}", entry, e);
        }
    }

    /**
     * 获取值（只读操作）
     *
     * @param key 键
     * @return 值，不存在则返回null
     */
    public String get(String key) {
        return data.get(key);
    }

    /**
     * 获取所有数据（用于调试）
     *
     * @return 所有键值对
     */
    public Map<String, String> getAllData() {
        return new ConcurrentHashMap<>(data);
    }

    /**
     * 获取数据大小
     *
     * @return 键值对数量
     */
    public int size() {
        return data.size();
    }
}
