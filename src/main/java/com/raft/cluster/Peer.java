package com.raft.cluster;

import java.io.Serializable;
import java.util.Objects;

/**
 * 集群中的节点信息
 *
 * 表示集群中的一个节点（包括自己和其他节点）
 * 包含节点的唯一标识、网络地址等信息
 */
public class Peer implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 节点的唯一标识符
     * 通常使用 "host:port" 格式
     */
    private String nodeId;

    /**
     * 节点的主机地址
     */
    private String host;

    /**
     * 节点的端口号
     */
    private int port;

    /**
     * Leader用于跟踪的信息：已发送给该节点的下一个日志条目的索引
     * 初始化为Leader最后一条日志索引+1
     */
    private long nextIndex;

    /**
     * Leader用于跟踪的信息：已复制到该节点的最高日志条目索引
     * 初始化为0，单调递增
     */
    private long matchIndex;

    public Peer() {
    }

    public Peer(String host, int port) {
        this.host = host;
        this.port = port;
        this.nodeId = host + ":" + port;
        this.nextIndex = 1;
        this.matchIndex = 0;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getNextIndex() {
        return nextIndex;
    }

    public void setNextIndex(long nextIndex) {
        this.nextIndex = nextIndex;
    }

    public long getMatchIndex() {
        return matchIndex;
    }

    public void setMatchIndex(long matchIndex) {
        this.matchIndex = matchIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Peer peer = (Peer) o;
        return Objects.equals(nodeId, peer.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }

    @Override
    public String toString() {
        return "Peer{" +
                "nodeId='" + nodeId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", nextIndex=" + nextIndex +
                ", matchIndex=" + matchIndex +
                '}';
    }
}
