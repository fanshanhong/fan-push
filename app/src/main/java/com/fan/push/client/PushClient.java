package com.fan.push.client;

import com.fan.push.message.Message;
import com.fan.push.util.GsonUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
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
    public static boolean HANDSHAKE_SUCCESS = false;


    public static boolean isReconnecting = false; // 是否正在重连


    public static ConnectionWatchdog watchdog;

    public static HeartBeatClientHandler heartBeatClientHandler;

    public void connect() {

        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();

        try {

            final Bootstrap bootstrap = new Bootstrap();
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);
            // 设置该选项以后，如果在两小时内没有数据的通信时，TCP会自动发送一个活动探测数据报文
            bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            // 设置禁用nagle算法
            bootstrap.option(ChannelOption.TCP_NODELAY, true);
//            bootstrap.option(ChannelOption.SO_TIMEOUT, 6000);
            watchdog = new ConnectionWatchdog(bootstrap, new HashedWheelTimer(), new InetSocketAddress("192.168.110.110", 10010));
            heartBeatClientHandler = new HeartBeatClientHandler(bootstrap);
            bootstrap.group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {

                            // 握手成功后, 才把  watchDog 和  IdleStateHandler加入到pipeline中做心跳和重连操作.
                            // 如果都没有握手成功, 就认为不是合法用户, 不需要这些
                            ch.pipeline().addLast("1",watchdog);
                            ch.pipeline().addLast("2",new IdleStateHandler(20, 5, 0, TimeUnit.SECONDS));

                            // LengthFieldPrepender 是个 MessageToMessageEncoder<ByteBuf>, 编码器
                            ch.pipeline().addLast("3", new LengthFieldPrepender(2));

                            // 基于帧长度的解码器
                            ch.pipeline().addLast("4", new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));


                            // 握手认证消息响应处理handler
                            // ch.pipeline().addLast(LoginAuthRespHandler.class.getSimpleName(), new LoginAuthRespHandler(imsClient));
                            // 心跳消息响应处理handler
                            ch.pipeline().addLast("5",heartBeatClientHandler);
                            ch.pipeline().addLast("6", new PushClientHandler());
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


            // 这块应该不需要是握手的情况下.
            // 这里说的是第一次连接都没成功, 那么就多试几次就好了, 如果还不行, 就只能关闭了
            // TODO:reconnect
        }
    }
}
