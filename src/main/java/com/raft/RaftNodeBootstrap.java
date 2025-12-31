package com.raft;

import com.raft.cluster.Peer;
import com.raft.config.RaftConfig;
import com.raft.core.RaftNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Raft节点启动类
 *
 * 用法示例：
 * 启动3个节点的集群：
 *
 * 节点1: java -jar simple-raft.jar localhost 8001 localhost:8002,localhost:8003
 * 节点2: java -jar simple-raft.jar localhost 8002 localhost:8001,localhost:8003
 * 节点3: java -jar simple-raft.jar localhost 8003 localhost:8001,localhost:8002
 *
 * 交互命令：
 * - set key value: 设置键值对
 * - get key: 获取值
 * - delete key: 删除键
 * - status: 查看节点状态
 * - log: 查看日志
 * - data: 查看所有数据
 * - exit: 退出
 */
public class RaftNodeBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(RaftNodeBootstrap.class);

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("用法: java -jar simple-raft.jar <host> <port> <peers>");
            System.out.println("示例: java -jar simple-raft.jar localhost 8001 localhost:8002,localhost:8003");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String nodeId = host + ":" + port;

        // 解析集群节点
        String[] peerAddrs = args[2].split(",");
        RaftConfig config = new RaftConfig(nodeId, host, port);

        for (String peerAddr : peerAddrs) {
            String[] parts = peerAddr.split(":");
            if (parts.length == 2) {
                String peerHost = parts[0];
                int peerPort = Integer.parseInt(parts[1]);
                config.addPeer(peerHost, peerPort);
            }
        }

        // 创建并启动Raft节点
        RaftNode node = new RaftNode(nodeId, host, port, config.getPeers());
        node.start();

        logger.info("==================================================");
        logger.info("Raft节点启动成功!");
        logger.info("节点ID: {}", nodeId);
        logger.info("集群节点数: {}", config.getPeers().size() + 1);
        logger.info("==================================================");
        logger.info("支持的命令:");
        logger.info("  set <key> <value>  - 设置键值对");
        logger.info("  get <key>          - 获取值");
        logger.info("  delete <key>       - 删除键");
        logger.info("  status             - 查看节点状态");
        logger.info("  log                - 查看日志");
        logger.info("  data               - 查看所有数据");
        logger.info("  exit               - 退出");
        logger.info("==================================================");

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("正在关闭Raft节点...");
            node.shutdown();
        }));

        // 启动交互式命令行
        startCommandLine(node);
    }

    /**
     * 启动交互式命令行
     *
     * @param node Raft节点
     */
    private static void startCommandLine(RaftNode node) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            try {
                System.out.print("\n> ");
                String line = scanner.nextLine().trim();

                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(" ", 3);
                String command = parts[0].toLowerCase();

                switch (command) {
                    case "set":
                        // SET命令：set key value
                        if (parts.length >= 3) {
                            String key = parts[1];
                            String value = parts[2];
                            String cmd = "SET " + key + " " + value;
                            boolean success = node.appendEntry(cmd);
                            if (success) {
                                System.out.println("命令已提交，等待复制和提交...");
                            } else {
                                System.out.println("失败：当前节点不是Leader");
                                if (node.getLeaderId() != null) {
                                    System.out.println("Leader是: " + node.getLeaderId());
                                }
                            }
                        } else {
                            System.out.println("用法: set <key> <value>");
                        }
                        break;

                    case "get":
                        // GET命令：get key
                        if (parts.length >= 2) {
                            String key = parts[1];
                            String value = node.getStateMachine().get(key);
                            if (value != null) {
                                System.out.println(key + " = " + value);
                            } else {
                                System.out.println(key + " 不存在");
                            }
                        } else {
                            System.out.println("用法: get <key>");
                        }
                        break;

                    case "delete":
                        // DELETE命令：delete key
                        if (parts.length >= 2) {
                            String key = parts[1];
                            String cmd = "DELETE " + key;
                            boolean success = node.appendEntry(cmd);
                            if (success) {
                                System.out.println("命令已提交，等待复制和提交...");
                            } else {
                                System.out.println("失败：当前节点不是Leader");
                                if (node.getLeaderId() != null) {
                                    System.out.println("Leader是: " + node.getLeaderId());
                                }
                            }
                        } else {
                            System.out.println("用法: delete <key>");
                        }
                        break;

                    case "status":
                        // 查看节点状态
                        System.out.println("==================================================");
                        System.out.println("节点ID: " + node.getNodeId());
                        System.out.println("节点状态: " + node.getState());
                        System.out.println("当前任期: " + node.getCurrentTerm());
                        System.out.println("Leader ID: " + (node.getLeaderId() != null ? node.getLeaderId() : "无"));
                        System.out.println("日志条目数: " + node.getLog().size());
                        System.out.println("已提交索引: " + node.getCommitIndex());
                        System.out.println("==================================================");
                        break;

                    case "log":
                        // 查看日志
                        System.out.println("==================================================");
                        System.out.println("日志条目:");
                        if (node.getLog().isEmpty()) {
                            System.out.println("  (空)");
                        } else {
                            for (int i = 0; i < node.getLog().size(); i++) {
                                System.out.println("  " + node.getLog().get(i));
                            }
                        }
                        System.out.println("==================================================");
                        break;

                    case "data":
                        // 查看所有数据
                        System.out.println("==================================================");
                        System.out.println("状态机数据:");
                        if (node.getStateMachine().size() == 0) {
                            System.out.println("  (空)");
                        } else {
                            node.getStateMachine().getAllData().forEach((k, v) ->
                                    System.out.println("  " + k + " = " + v));
                        }
                        System.out.println("==================================================");
                        break;

                    case "exit":
                    case "quit":
                        // 退出
                        System.out.println("正在退出...");
                        node.shutdown();
                        System.exit(0);
                        break;

                    case "help":
                        // 帮助
                        System.out.println("支持的命令:");
                        System.out.println("  set <key> <value>  - 设置键值对");
                        System.out.println("  get <key>          - 获取值");
                        System.out.println("  delete <key>       - 删除键");
                        System.out.println("  status             - 查看节点状态");
                        System.out.println("  log                - 查看日志");
                        System.out.println("  data               - 查看所有数据");
                        System.out.println("  exit               - 退出");
                        break;

                    default:
                        System.out.println("未知命令: " + command);
                        System.out.println("输入 help 查看帮助");
                        break;
                }

            } catch (Exception e) {
                logger.error("命令执行失败", e);
                System.out.println("错误: " + e.getMessage());
            }
        }
    }
}
