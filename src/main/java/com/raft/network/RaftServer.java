package com.raft.network;

import com.alibaba.fastjson2.JSON;
import com.raft.core.RaftNode;
import com.raft.rpc.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Raft服务端
 *
 * 负责接收其他节点的RPC请求
 * 使用Netty实现网络通信
 */
public class RaftServer {
    private static final Logger logger = LoggerFactory.getLogger(RaftServer.class);

    /**
     * 监听主机
     */
    private final String host;

    /**
     * 监听端口
     */
    private final int port;

    /**
     * Raft节点引用
     */
    private final RaftNode raftNode;

    /**
     * Boss线程组（接受连接）
     */
    private EventLoopGroup bossGroup;

    /**
     * Worker线程组（处理I/O）
     */
    private EventLoopGroup workerGroup;

    /**
     * 服务端Channel
     */
    private Channel serverChannel;

    public RaftServer(String host, int port, RaftNode raftNode) {
        this.host = host;
        this.port = port;
        this.raftNode = raftNode;
    }

    /**
     * 启动服务端
     */
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(4);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
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
                            pipeline.addLast(new RaftServerHandler());
                        }
                    });

            // 绑定端口
            ChannelFuture future = bootstrap.bind(host, port).sync();
            serverChannel = future.channel();

            logger.info("Raft服务端启动成功: {}:{}", host, port);

        } catch (Exception e) {
            logger.error("Raft服务端启动失败", e);
            shutdown();
        }
    }

    /**
     * 关闭服务端
     */
    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("Raft服务端已关闭");
    }

    /**
     * 服务端处理器
     */
    private class RaftServerHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            try {
                // 解析请求（格式：requestId:json）
                int colonIndex = msg.indexOf(':');
                if (colonIndex <= 0) {
                    logger.warn("无效的消息格式: {}", msg);
                    return;
                }

                String requestId = msg.substring(0, colonIndex);
                String json = msg.substring(colonIndex + 1);

                RaftMessage request = JSON.parseObject(json, RaftMessage.class);
                RaftMessage response = null;

                // 根据消息类型处理请求
                switch (request.getType()) {
                    case REQUEST_VOTE_REQ:
                        // 解析RequestVoteRequest
                        RequestVoteRequest voteReq = JSON.parseObject(
                                JSON.toJSONString(request.getBody()), RequestVoteRequest.class);
                        // 处理请求
                        RequestVoteResponse voteResp = raftNode.handleRequestVote(voteReq);
                        // 构造响应
                        response = new RaftMessage(RaftMessage.MessageType.REQUEST_VOTE_RESP, voteResp);
                        break;

                    case APPEND_ENTRIES_REQ:
                        // 解析AppendEntriesRequest
                        AppendEntriesRequest appendReq = JSON.parseObject(
                                JSON.toJSONString(request.getBody()), AppendEntriesRequest.class);
                        // 处理请求
                        AppendEntriesResponse appendResp = raftNode.handleAppendEntries(appendReq);
                        // 构造响应
                        response = new RaftMessage(RaftMessage.MessageType.APPEND_ENTRIES_RESP, appendResp);
                        break;

                    default:
                        logger.warn("未知的消息类型: {}", request.getType());
                        return;
                }

                // 发送响应
                if (response != null) {
                    String respJson = requestId + ":" + JSON.toJSONString(response);
                    ctx.writeAndFlush(respJson);
                }

            } catch (Exception e) {
                logger.error("处理请求失败", e);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("服务端异常", cause);
            ctx.close();
        }
    }
}
