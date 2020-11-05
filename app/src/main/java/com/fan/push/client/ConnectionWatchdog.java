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

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        boolean doReconnect = isReconnectNeeded();
        if (doReconnect) {
            if (attempts < 12) {
                attempts++;
            }
            long timeout = 2 << attempts;
            logger.info("attempts=" + attempts + "    timeout=" + timeout);
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
        ChannelFuture future;
        try {

            synchronized (bootstrap) {
                bootstrap.handler(new ChannelInitializer<Channel>() {

                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(PushClient.watchdog);

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
                future = bootstrap.connect(remoteAddress).sync();
            }

            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) throws Exception {
                    boolean succeed = f.isSuccess();

                    logger.warn("Reconnects with {}, {}.", remoteAddress, succeed ? "succeed" : "failed");

                    if (!succeed) {
                        f.channel().pipeline().fireChannelInactive();
                    }
                }
            });
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 重新
                if (attempts < 12) {
                    attempts++;
                }
                long t = 2 << attempts;
                logger.info("attempts=" + attempts + "    timeout=" + timeout);
                timer.newTimeout(this, t, TimeUnit.SECONDS);
        }
    }

    private boolean isReconnectNeeded() {
//        return isStarted() ;
        //&& (group == null || (group.size() < group.getCapacity()));


        // 如果是握手成功的情况下, 才需要重连.
        // 并且,当前不是正在连接中的状态. 有可能正在连呢,又去连接了.
        return true;
    }
}
