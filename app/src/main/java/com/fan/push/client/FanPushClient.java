package com.fan.push.client;

import com.fan.push.server.PushServerHandler;

import java.net.InetSocketAddress;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class FanPushClient {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PushServerHandler.class);




    void connect() {

        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();

        try {

            Bootstrap bootstrap = new Bootstrap();

            bootstrap.group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // LengthFieldPrepender 是个 MessageToMessageEncoder<ByteBuf>, 编码器
                            ch.pipeline().addLast("lengthFieldEncoder", new LengthFieldPrepender(2));

                            // 基于帧长度的解码器
                            ch.pipeline().addLast("lengthFieldDecoder", new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));

                            ch.pipeline().addLast("clientHandler", new PushClientHandler());
                        }
                    });

            logger.info("正在连接中...");

            ChannelFuture future = bootstrap.connect(new InetSocketAddress("127.0.0.1", 10010));
            final Channel channel = future.channel();

            future.addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> future) throws Exception {

                    if (future.isSuccess() && channel != null) {
                        logger.info("连接成功");
                    } else {
                        logger.info("连接失败");
                    }
                }
            });

            future.sync();
            future.channel().closeFuture().sync();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {

            // 这里不要优雅的关闭, 要重连
            eventLoopGroup.shutdownGracefully();
        }
    }

}
