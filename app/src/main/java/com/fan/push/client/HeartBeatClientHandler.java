package com.fan.push.client;

import com.fan.push.message.Message;
import com.fan.push.util.GsonUtil;
import com.fan.push.util.LoggerUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.HashedWheelTimer;

@ChannelHandler.Sharable
public class HeartBeatClientHandler extends ChannelInboundHandlerAdapter {

    private Bootstrap bootstrap;
    Object object= new Object();

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("HeartBeatClientHandler exceptionCaught");
        return;
    }

    public HeartBeatClientHandler(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        ctx.fireChannelActive();
    }

    private boolean isReconnectNeeded() {
        // TODO:如果是握手成功的情况下, 才需要重连.
        // 并且,当前不是正在连接中的状态. 有可能正在连呢,又去连接了.
        if ((!PushClient.isReconnecting) && PushClient.HANDSHAKE_SUCCESS == true) {
            return true;
        }
        return true;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            switch (state) {
                case READER_IDLE: {
                    // 规定时间内没收到服务端心跳包响应，进行重连操作
//                        try {
//                            Thread.sleep(3000);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }

                    // 为啥  又要监听  读超时, 又要在  WatchDog的 inActive 中重连?
                    // 因为, 如果是服务器把客户端的连接关闭了, 只进入  WatchDog的 inActive, 不会再进入这里
                    //      如果是服务器网断了之类的情况,客户端不会进入 inActive, 只能通过这里的 读超时来判断
                    LoggerUtil.logger.info("PushClient.connectState=" + PushClient.isReconnecting + "  PushClient.HANDSHAKE_SUCCESS=" + PushClient.HANDSHAKE_SUCCESS);
                    if (!isReconnectNeeded()) {

                        LoggerUtil.logger.info("好像正在连接? 或者没握手成功, 就不需要重连了");
                        return;
                    }

                    LoggerUtil.logger.info("规定时间内没收到服务端心跳包响应，进行重连操作");

                    PushClient.isReconnecting = true;

                    //closeChannel(ctx.channel());
//                    try {
//                        // 先释放EventLoop线程组
//                        if (bootstrap != null) {
//                            bootstrap.group().shutdownGracefully();
//                        }
//                    } finally {
//                        bootstrap = null;
//                    }

                    // TODO: reconnect
                    ChannelFuture future;
                    try {

                        synchronized (object) {
//                            final Bootstrap bootstrap = new Bootstrap();
                            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);
                            // 设置该选项以后，如果在两小时内没有数据的通信时，TCP会自动发送一个活动探测数据报文
                            bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
                            // 设置禁用nagle算法
                            bootstrap.option(ChannelOption.TCP_NODELAY, true);
//                            bootstrap.group(new NioEventLoopGroup())
//                                    .channel(NioSocketChannel.class);
                            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);
                            // 设置该选项以后，如果在两小时内没有数据的通信时，TCP会自动发送一个活动探测数据报文
                            bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
                            // 设置禁用nagle算法
                            bootstrap.option(ChannelOption.TCP_NODELAY, true);
                            //bootstrap.option(ChannelOption.SO_TIMEOUT, 6000);
                            bootstrap.option(ChannelOption.ALLOW_HALF_CLOSURE, true);
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
                                    ch.pipeline().addLast("5",new HeartBeatClientHandler(bootstrap));
                                    ch.pipeline().addLast("6", new PushClientHandler());
//                                    ch.pipeline().addLast("7", new ExceptionCaughtHandler());
                                }
                            });

                            future = bootstrap.connect("192.168.0.106", 10010).sync();
                        }

                        future.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture f) throws Exception {
                                boolean succeed = f.isSuccess();

                                if (!succeed) {
                                    f.channel().pipeline().fireChannelInactive();
                                } else {
                                    PushClient.isReconnecting = false;
                                }
                            }
                        });
                        future.channel().closeFuture().sync();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                    }
                    break;
                }

                case WRITER_IDLE: {
                    // 规定时间内没向服务端发送心跳包，即发送一个心跳包
                    // TODO: send heart beat
                    LoggerUtil.logger.info("发送一个心跳包");
                    ctx.writeAndFlush(Unpooled.wrappedBuffer(GsonUtil.getInstance().toJson(Message.obtainPingMessage()).getBytes(CharsetUtil.UTF_8)));
                    break;
                }
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println(":11111");
        ctx.fireChannelInactive();
    }

    /**
     * 关闭channel
     */
    private void closeChannel(Channel channel) {
        bootstrap.group().shutdownGracefully();
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
                        channel.close();
                    } catch (Exception ex) {
                    }
                    try {
                        channel.eventLoop().shutdownGracefully();
                    } catch (Exception ex) {
                    }

                    channel = null;
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

// 一月 07, 2020 3:45:10 下午 com.fan.push.server.PushServerHandler exceptionCaught
//严重: An I/O exception was caught: java.io.IOException: 远程主机强迫关闭了一个现有的连接。
//	at sun.nio.ch.SocketDispatcher.read0(Native Method)
//	at sun.nio.ch.SocketDispatcher.read(SocketDispatcher.java:43)
//	at sun.nio.ch.IOUtil.readIntoNativeBuffer(IOUtil.java:223)
//	at sun.nio.ch.IOUtil.read(IOUtil.java:192)
//	at sun.nio.ch.SocketChannelImpl.read(SocketChannelImpl.java:380)
//	at io.netty.buffer.PooledByteBuf.setBytes(PooledByteBuf.java:253)
//	at io.netty.buffer.AbstractByteBuf.writeBytes(AbstractByteBuf.java:1133)
//	at io.netty.channel.socket.nio.NioSocketChannel.doReadBytes(NioSocketChannel.java:350)
//	at io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:148)
//	at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:714)
//	at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:650)
//	at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:576)
//	at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:493)
//	at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:989)
//	at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
//	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
//	at java.lang.Thread.run(Thread.java:748)
