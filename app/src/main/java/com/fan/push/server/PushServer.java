package com.fan.push.server;

import com.fan.push.message.Message;
import com.fan.push.util.GsonUtil;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;


/**
 * @Description: 服务器启动类
 * @Author: fan
 * @Date: 2020-9-19 11:19
 * @Modify:
 */
public class PushServer {

    public static void main(String[] args) {
        PushServer pushServer = new PushServer();
        pushServer.bind();
    }

    // 消息重发管理器
    public MessageRetryManager messageRetryManager = new MessageRetryManager(this);

    /**
     * 发送(推送)消息给 userId
     *
     * @param userId            客户端userId
     * @param message           消息
     * @param addToRetryManager 是否要加入到重发管理器中进行管理
     */
    public void sendMsg(String userId, Message message, boolean addToRetryManager) {

        if (addToRetryManager) {
            messageRetryManager.add(userId, message);
        }

        if (ChannelHolder.getInstance().isOnline(userId)) {
            ChannelHolder.getInstance().getChannelByUserId(userId).writeAndFlush(Unpooled.copiedBuffer(GsonUtil.getInstance().toJson(message).getBytes(CharsetUtil.UTF_8)));
        }
    }

    /**
     * 将消息从 重发管理器中移除
     *
     * @param userId
     * @param message
     */
    public void removeMsgFromRetryManager(String userId, Message message) {
        if (StringUtil.isNullOrEmpty(userId)) {
            throw new IllegalArgumentException("removeMsgFromRetryManager userId can not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("removeMsgFromRetryManager message can not be null");
        }
        messageRetryManager.remove(userId, message);
    }

    /**
     * 服务器初始化, 绑定端口, 并开始监听
     */
    private void bind() {

        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();

            // 服务器端相关配置
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)// 指定 bossGroup 使用 NioServerSocketChannel 来处理连接请求
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel ch) throws Exception {

                            // 超时处理器, 当 23秒内未收到数据, 则触发pipeline 中的 Handler 的userEventTriggered() 方法
                            ch.pipeline().addLast("idleStateHandler", new IdleStateHandler(23, 0, 0, TimeUnit.SECONDS));
                            ch.pipeline().addLast("heartBeatServerHandler", new HeartBeatServerHandler(PushServer.this));

                            // LengthFieldPrepender 是个 MessageToMessageEncoder<ByteBuf>, 编码器, 出站处理器
                            // 输入类型是ByteBuf, 输出类型也是ByteBuf
                            ch.pipeline().addLast("lengthFieldEncoder", new LengthFieldPrepender(2));

                            // LengthFieldBasedFrameDecoder:基于帧长度的解码器, 解码器, 入站处理器
                            // 输入类型是ByteBuf, 输出类型也是ByteBuf
                            ch.pipeline().addLast("lengthFieldDecoder", new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));

                            ch.pipeline().addLast("serverHandler", new PushServerHandler(PushServer.this));
                        }
                    });

            // 绑定端口并且同步处理
            // 这里启动了服务器
            ChannelFuture channelFuture = serverBootstrap.bind(10010).sync();

            // 对关闭通道进行监听
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}