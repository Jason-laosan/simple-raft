package com.raft.config;

import com.raft.cluster.Peer;

import java.util.ArrayList;
import java.util.List;

/**
 * Raft节点配置
 *
 * 包含节点和集群的配置信息
 */
public class RaftConfig {

    /**
     * 当前节点ID
     */
    private String nodeId;

    /**
     * 当前节点主机
     */
    private String host;

    /**
     * 当前节点端口
     */
    private int port;

    /**
     * 集群中的其他节点
     */
    private List<Peer> peers = new ArrayList<>();

    public RaftConfig() {
    }

    public RaftConfig(String nodeId, String host, int port) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
    }

    /**
     * 添加集群节点
     *
     * @param host 节点主机
     * @param port 节点端口
     */
    public void addPeer(String host, int port) {
        peers.add(new Peer(host, port));
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

    public List<Peer> getPeers() {
        return peers;
    }

    public void setPeers(List<Peer> peers) {
        this.peers = peers;
    }
}
