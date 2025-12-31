package com.raft;

import com.raft.cluster.Peer;
import com.raft.core.LogEntry;
import com.raft.core.NodeState;
import com.raft.core.RaftNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Raft节点基础测试
 *
 * 测试Raft节点的基本功能
 */
public class RaftNodeTest {

    /**
     * 测试节点初始化
     */
    @Test
    public void testNodeInitialization() {
        List<Peer> peers = new ArrayList<>();
        peers.add(new Peer("localhost", 8002));
        peers.add(new Peer("localhost", 8003));

        RaftNode node = new RaftNode("localhost:8001", "localhost", 8001, peers);

        // 验证初始状态
        assertEquals("节点初始状态应为FOLLOWER", NodeState.FOLLOWER, node.getState());
        assertEquals("初始任期应为0", 0, node.getCurrentTerm());
        assertNull("初始Leader应为null", node.getLeaderId());
        assertTrue("初始日志应为空", node.getLog().isEmpty());
        assertEquals("初始commitIndex应为0", 0, node.getCommitIndex());
    }

    /**
     * 测试日志条目创建
     */
    @Test
    public void testLogEntryCreation() {
        LogEntry entry = new LogEntry(1, 1, "SET name Alice");

        assertEquals("任期号应为1", 1, entry.getTerm());
        assertEquals("索引应为1", 1, entry.getIndex());
        assertEquals("命令应为'SET name Alice'", "SET name Alice", entry.getCommand());
    }

    /**
     * 测试状态机操作
     */
    @Test
    public void testStateMachine() {
        List<Peer> peers = new ArrayList<>();
        RaftNode node = new RaftNode("localhost:8001", "localhost", 8001, peers);

        // 创建并应用日志条目
        LogEntry entry1 = new LogEntry(1, 1, "SET name Alice");
        LogEntry entry2 = new LogEntry(1, 2, "SET age 25");

        node.getStateMachine().apply(entry1);
        node.getStateMachine().apply(entry2);

        // 验证状态机状态
        assertEquals("name应为Alice", "Alice", node.getStateMachine().get("name"));
        assertEquals("age应为25", "25", node.getStateMachine().get("age"));
        assertEquals("状态机应有2个键值对", 2, node.getStateMachine().size());

        // 测试删除操作
        LogEntry entry3 = new LogEntry(1, 3, "DELETE age");
        node.getStateMachine().apply(entry3);

        assertNull("age应已删除", node.getStateMachine().get("age"));
        assertEquals("状态机应有1个键值对", 1, node.getStateMachine().size());
    }

    /**
     * 测试Peer对象
     */
    @Test
    public void testPeer() {
        Peer peer = new Peer("localhost", 8001);

        assertEquals("节点ID应为localhost:8001", "localhost:8001", peer.getNodeId());
        assertEquals("主机应为localhost", "localhost", peer.getHost());
        assertEquals("端口应为8001", 8001, peer.getPort());
        assertEquals("nextIndex应初始化为1", 1, peer.getNextIndex());
        assertEquals("matchIndex应初始化为0", 0, peer.getMatchIndex());
    }

    /**
     * 测试节点ID生成
     */
    @Test
    public void testNodeIdGeneration() {
        List<Peer> peers = new ArrayList<>();
        RaftNode node = new RaftNode("localhost:8001", "localhost", 8001, peers);

        assertEquals("节点ID应为localhost:8001", "localhost:8001", node.getNodeId());
    }
}
