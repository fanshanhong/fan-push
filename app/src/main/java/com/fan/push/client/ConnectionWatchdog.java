/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fan.push.client;

import com.fan.push.util.StackTraceUtil;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;


/**
 * Connections watchdog.
 * <p>
 * jupiter
 * org.jupiter.transport.netty.handler.connector
 *
 * @author jiachun.fjc
 */
@ChannelHandler.Sharable
public class ConnectionWatchdog extends ChannelInboundHandlerAdapter implements TimerTask {
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("===watch dog======="+StackTraceUtil.stackTrace(cause));
    }

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ConnectionWatchdog.class);

    private static final int ST_STARTED = 1;
    private static final int ST_STOPPED = 2;

    private final Bootstrap bootstrap;
    private final Timer timer;
    private final SocketAddress remoteAddress;
    private volatile int state = ST_STARTED; // 自动重连, 默认开启
    private int attempts;

    public ChannelHandler[] handlers;

    public ConnectionWatchdog(Bootstrap bootstrap, Timer timer, SocketAddress remoteAddress) {
        this.bootstrap = bootstrap;
        this.timer = timer;
        this.remoteAddress = remoteAddress;
    }

    public boolean isStarted() {
        return state == ST_STARTED;
    }

    public void start() {
        state = ST_STARTED;
    }

    public void stop() {
        state = ST_STOPPED;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();

        // 清零
        attempts = 0;

        logger.info("Connects with {}.", ch);

        ctx.fireChannelActive();
    }

    Channel channel;
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        boolean doReconnect = isReconnectNeeded();
        if (doReconnect) {
            if (attempts < 9) {
                attempts++;
            }
            long timeout = 2 << attempts;
            logger.info("attempts=" + attempts + "    timeout=" + timeout);
            channel = ctx.channel();
            timer.newTimeout(this, timeout, TimeUnit.SECONDS);
        }

        logger.warn("Disconnects with {}, address: {}, reconnect: {}.", ctx.channel(), remoteAddress, doReconnect);

        ctx.fireChannelInactive();
    }

    @Override
    public void run(Timeout timeout) {
        if (!isReconnectNeeded()) {
            logger.warn("Cancel reconnecting with {}.", remoteAddress);
            return;
        }
        PushClient.isReconnecting = true;
        ChannelFuture future;
        try {

            synchronized (bootstrap) {
                closeChannel(channel);
                bootstrap.handler(new ChannelInitializer<Channel>() {

                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast("1",PushClient.watchdog);

                        ch.pipeline().addLast("2",new IdleStateHandler(20, 5, 0, TimeUnit.SECONDS));

                        // LengthFieldPrepender 是个 MessageToMessageEncoder<ByteBuf>, 编码器
                        ch.pipeline().addLast("3", new LengthFieldPrepender(2));

                        // 基于帧长度的解码器
                        ch.pipeline().addLast("4", new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));


                        // 握手认证消息响应处理handler
                        // ch.pipeline().addLast(LoginAuthRespHandler.class.getSimpleName(), new LoginAuthRespHandler(imsClient));
                        // 心跳消息响应处理handler
                        ch.pipeline().addLast("5",PushClient.heartBeatClientHandler);
                        ch.pipeline().addLast("6", new PushClientHandler());
                    }
                });

                future = bootstrap.connect(remoteAddress).sync();
            }

            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) throws Exception {
                    boolean succeed = f.isSuccess();

                    logger.warn("Reconnects with {}, {}.", remoteAddress, succeed ? "succeed" : "failed");

                    PushClient.isReconnecting = false;
                    if (!succeed) {
                        f.channel().pipeline().fireChannelInactive();
                    }
                }
            });
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("watch dog connect exception!!!!");
            PushClient.isReconnecting = false;
            // 重新
            if (attempts < 9) {
                attempts++;
            }
            long t = 2 << attempts;
            logger.info("attempts=" + attempts + "    timeout=" + timeout);
            timer.newTimeout(this, t, TimeUnit.SECONDS);
        } finally {

        }
    }

    private boolean isReconnectNeeded() {
//        return isStarted() ;
        //&& (group == null || (group.size() < group.getCapacity()));


        // TODO:如果是握手成功的情况下, 才需要重连.
        // 并且,当前不是正在连接中的状态. 有可能正在连呢,又去连接了.
        if(!PushClient.isReconnecting && PushClient.HANDSHAKE_SUCCESS == true) {
            return true;
        }
        return false;
    }
    /**
     * 关闭channel
     */
    private void closeChannel(Channel channel) {
        System.out.println("关闭channel");
        try {
            if (channel != null) {
                try {
                    removeHandler(channel,"1");
                    removeHandler(channel,"2");
                    removeHandler(channel,"3");
                    removeHandler(channel,"4");
                    removeHandler(channel,"5");
                    removeHandler(channel,"6");
                } finally {
                    try {
//                        channel.close();
                    } catch (Exception ex) {
                    }
                    try {
//                        channel.eventLoop().shutdownGracefully();
                    } catch (Exception ex) {
                    }

//                    channel = null;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("关闭channel出错，reason:" + ex.getMessage());
        }
    }
    /**
     * 移除指定handler
     *
     * @param handlerName
     */
    private void removeHandler(Channel channel, String handlerName) {
        try {
            if (channel.pipeline().get(handlerName) != null) {
                channel.pipeline().remove(handlerName);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("移除handler失败，handlerName=" + handlerName);
        }
    }
}
