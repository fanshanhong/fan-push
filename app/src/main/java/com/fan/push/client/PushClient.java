package com.fan.push.client;

import com.fan.push.message.Message;
import com.fan.push.server.PushServerHandler;
import com.fan.push.util.GsonUtil;
import com.google.gson.Gson;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import static com.fan.push.util.LoggerUtil.logger;

public class PushClient {

    public Channel channel = null;

    public static ConnectionWatchdog watchdog;

    public void connect() {

        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();

        try {

            final Bootstrap bootstrap = new Bootstrap();
            final ConnectionWatchdog connectionWatchdog = new ConnectionWatchdog(bootstrap, new HashedWheelTimer(), new InetSocketAddress("192.168.110.110", 10010));


            bootstrap.group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(connectionWatchdog);
                            ch.pipeline().addLast(new IdleStateHandler(20, 5, 30, TimeUnit.SECONDS));

                            // LengthFieldPrepender 是个 MessageToMessageEncoder<ByteBuf>, 编码器
                            ch.pipeline().addLast("lengthFieldEncoder", new LengthFieldPrepender(2));

                            // 基于帧长度的解码器
                            ch.pipeline().addLast("lengthFieldDecoder", new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));


                            // 握手认证消息响应处理handler
                            // ch.pipeline().addLast(LoginAuthRespHandler.class.getSimpleName(), new LoginAuthRespHandler(imsClient));
                            // 心跳消息响应处理handler
                            ch.pipeline().addLast(new HeartBeatClientHandler());
                            ch.pipeline().addLast("clientHandler", new PushClientHandler());
                        }
                    });

            logger.info("正在连接中...");

            ChannelFuture future = bootstrap.connect(new InetSocketAddress("192.168.110.110", 10010)).sync();

            channel = future.channel();

            future.addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> futureTask) throws Exception {

                    if (futureTask.isSuccess() && channel != null) {
                        logger.info("连接成功");

                        // 构造一条握手消息, 并发送
                        Message handshakeMessage = new Message();
                        handshakeMessage.setMessageType(1001);
                        handshakeMessage.setContent("username=111&password=222");
                        channel.writeAndFlush(Unpooled.wrappedBuffer(GsonUtil.getInstance().toJson(handshakeMessage).getBytes(CharsetUtil.UTF_8)));

                    } else {
                        logger.info("连接失败");
                    }
                }
            });

            future.channel().closeFuture().sync();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 这里不要优雅的关闭, 要重连
//            eventLoopGroup.shutdownGracefully();


            // 如果是握手成功的状态下, 就需要重连
        }
    }
}
