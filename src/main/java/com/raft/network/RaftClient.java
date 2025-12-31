package com.raft.network;

import com.alibaba.fastjson2.JSON;
import com.raft.cluster.Peer;
import com.raft.rpc.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Raft客户端
 *
 * 负责向其他节点发送RPC请求
 * 使用Netty实现网络通信
 */
public class RaftClient {
    private static final Logger logger = LoggerFactory.getLogger(RaftClient.class);

    /**
     * Netty客户端启动器
     */
    private final Bootstrap bootstrap;

    /**
     * 工作线程组
     */
    private final EventLoopGroup workerGroup;

    /**
     * 连接缓存
     * key: nodeId, value: Channel
     */
    private final Map<String, Channel> channelCache = new ConcurrentHashMap<>();

    /**
     * 请求超时时间（毫秒）
     */
    private static final int REQUEST_TIMEOUT = 3000;

    /**
     * 等待响应的Future
     * key: requestId, value: CompletableFuture
     */
    private final Map<String, CompletableFuture<RaftMessage>> pendingRequests = new ConcurrentHashMap<>();

    public RaftClient() {
        workerGroup = new NioEventLoopGroup(4);
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 添加长度字段解码器（解决粘包/拆包）
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4));
                        pipeline.addLast(new LengthFieldPrepender(4));
                        // 添加字符串编解码器
                        pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                        pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                        // 添加业务处理器
                        pipeline.addLast(new RaftClientHandler());
                    }
                });
    }

    /**
     * 发送RequestVote RPC
     *
     * @param peer 目标节点
     * @param request 请求
     * @return 响应
     */
    public RequestVoteResponse requestVote(Peer peer, RequestVoteRequest request) throws Exception {
        RaftMessage message = new RaftMessage(RaftMessage.MessageType.REQUEST_VOTE_REQ, request);
        RaftMessage response = sendRequest(peer, message);
        return (RequestVoteResponse) response.getBody();
    }

    /**
     * 发送AppendEntries RPC
     *
     * @param peer 目标节点
     * @param request 请求
     * @return 响应
     */
    public AppendEntriesResponse appendEntries(Peer peer, AppendEntriesRequest request) throws Exception {
        RaftMessage message = new RaftMessage(RaftMessage.MessageType.APPEND_ENTRIES_REQ, request);
        RaftMessage response = sendRequest(peer, message);
        return (AppendEntriesResponse) response.getBody();
    }

    /**
     * 发送请求
     *
     * @param peer 目标节点
     * @param message 消息
     * @return 响应消息
     */
    private RaftMessage sendRequest(Peer peer, RaftMessage message) throws Exception {
        Channel channel = getChannel(peer);
        if (channel == null || !channel.isActive()) {
            throw new Exception("无法连接到节点: " + peer.getNodeId());
        }

        // 生成请求ID
        String requestId = generateRequestId();

        // 创建Future等待响应
        CompletableFuture<RaftMessage> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        // 发送请求（在消息前加上requestId）
        String json = requestId + ":" + JSON.toJSONString(message);
        channel.writeAndFlush(json);

        try {
            // 等待响应（带超时）
            return future.get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new Exception("请求超时: " + peer.getNodeId());
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    /**
     * 获取到指定节点的连接
     *
     * @param peer 目标节点
     * @return Channel
     */
    private Channel getChannel(Peer peer) {
        Channel channel = channelCache.get(peer.getNodeId());

        // 如果连接不存在或已关闭，创建新连接
        if (channel == null || !channel.isActive()) {
            try {
                ChannelFuture future = bootstrap.connect(peer.getHost(), peer.getPort()).sync();
                channel = future.channel();
                channelCache.put(peer.getNodeId(), channel);
                logger.info("连接到节点: {}", peer.getNodeId());
            } catch (Exception e) {
                logger.error("连接节点失败: {}", peer.getNodeId(), e);
                return null;
            }
        }

        return channel;
    }

    /**
     * 生成请求ID
     */
    private String generateRequestId() {
        return System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(10000);
    }

    /**
     * 关闭客户端
     */
    public void shutdown() {
        // 关闭所有连接
        for (Channel channel : channelCache.values()) {
            if (channel.isActive()) {
                channel.close();
            }
        }
        channelCache.clear();

        // 关闭线程组
        workerGroup.shutdownGracefully();
        logger.info("Raft客户端已关闭");
    }

    /**
     * 客户端处理器
     */
    private class RaftClientHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            try {
                // 解析响应（格式：requestId:json）
                int colonIndex = msg.indexOf(':');
                if (colonIndex > 0) {
                    String requestId = msg.substring(0, colonIndex);
                    String json = msg.substring(colonIndex + 1);

                    RaftMessage response = JSON.parseObject(json, RaftMessage.class);

                    // 根据消息类型反序列化body
                    if (response.getType() == RaftMessage.MessageType.REQUEST_VOTE_RESP) {
                        RequestVoteResponse body = JSON.parseObject(
                                JSON.toJSONString(response.getBody()), RequestVoteResponse.class);
                        response.setBody(body);
                    } else if (response.getType() == RaftMessage.MessageType.APPEND_ENTRIES_RESP) {
                        AppendEntriesResponse body = JSON.parseObject(
                                JSON.toJSONString(response.getBody()), AppendEntriesResponse.class);
                        response.setBody(body);
                    }

                    // 完成Future
                    CompletableFuture<RaftMessage> future = pendingRequests.get(requestId);
                    if (future != null) {
                        future.complete(response);
                    }
                }
            } catch (Exception e) {
                logger.error("处理响应失败", e);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("客户端异常", cause);
            ctx.close();
        }
    }
}
