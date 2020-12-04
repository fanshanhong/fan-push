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
import io.netty.util.CharsetUtil;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import static com.fan.push.client.ServerConfig.SERVER_IP;
import static com.fan.push.client.ServerConfig.SERVER_PORT;
import static com.fan.push.util.LoggerUtil.logger;

/**
 * @Description: 客户端启动类
 * @Author: fan
 * @Date: 2020-9-19 21:19
 * @Modify:
 */
public class PushClient {

    public static void main(String[] args) {
        // int a = 2<<12;   //  4   8  16  32  64  128  256 512 1024 2048 4096  8192
        // System.out.println(a);
        PushClient.getInstance().initBootStrap();
        PushClient.getInstance().connect();
    }

    private static PushClient instance = new PushClient();

    private PushClient() {

    }

    public static PushClient getInstance() {
        return instance;
    }


    // 客户端的 userId, 目前就这一个, 用于测试
    public static final String MY_CLIENT_USER_ID = "fanshanhong";

    // 连接状态：连接中
    public static final int CONNECT_STATE_CONNECTING = 0;
    // 连接状态：连接成功
    public static final int CONNECT_STATE_SUCCESSFUL = 1;
    // 连接状态：连接失败
    public static final int CONNECT_STATE_FAILURE = -1;

    // 全局, 是否关闭
    public static boolean isClosed = false;

    // 当前连接状态
    public static int connectStatus = CONNECT_STATE_FAILURE;

    private Bootstrap bootstrap;

    // 与服务端的连接
    private Channel channel = null;

    // 当前尝试重连的次数
    private int attempts = 0;

    // 最大重连次数
    private static final int MAX_ATTEMPTS = 12;

    // Netty 提供的定时器
    private Timer timer = new HashedWheelTimer();

    // 当收到新消息时候的回调
    private INewMessageListener newMessageListener;

    // 连接状态变化回调
    private IConnectStatusListener connectStatusListener;

    /**
     * 当前尝试次数清零
     */
    public void clearAttempts() {
        attempts = 0;
    }

    /**
     * 初始化 bootstrap
     */
    public void initBootStrap() {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        // 设置该选项以后，如果在两小时内没有数据的通信时，TCP会自动发送一个活动探测数据报文
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        // 设置禁用nagle算法
        bootstrap.option(ChannelOption.TCP_NODELAY, true);

        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class);
    }

    /**
     * 连接服务器
     */
    public void connect() {
        connectStatus = CONNECT_STATE_CONNECTING;
        if (PushClient.getInstance().getConnectStatusListener() != null) {
            PushClient.getInstance().getConnectStatusListener().connecting();
        }
        try {

            logger.info("正在连接中...");

            closeChannel(channel);

            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    for (ChannelHandler handler : ChannelHandlerHolder.handlers()) {
                        ch.pipeline().addLast(handler.getClass().getSimpleName(), handler);
                    }
                }
            });

            ChannelFuture future = bootstrap.connect(new InetSocketAddress(SERVER_IP, SERVER_PORT)).sync();

            channel = future.channel();

            future.addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> futureTask) throws Exception {
                    // 连接完成

                    boolean succeed = futureTask.isSuccess();

                    logger.warn("Reconnects with {}, {}.", SERVER_IP + ":" + SERVER_PORT, succeed ? "succeed" : "failed");

                    if (succeed && channel != null) {
                        logger.info("连接成功");
                        connectStatus = CONNECT_STATE_SUCCESSFUL;
                        if (PushClient.getInstance().getConnectStatusListener() != null) {
                            PushClient.getInstance().getConnectStatusListener().connectSuccess();
                        }
                        // 构造一条握手消息, 并发送
                        Message handshakeMessage = new Message(1001, MY_CLIENT_USER_ID, "server");
                        channel.writeAndFlush(Unpooled.wrappedBuffer(GsonUtil.getInstance().toJson(handshakeMessage).getBytes(CharsetUtil.UTF_8)));

                    } else {
                        logger.info("连接失败");
                        connectStatus = CONNECT_STATE_FAILURE;
                        if (PushClient.getInstance().getConnectStatusListener() != null) {
                            PushClient.getInstance().getConnectStatusListener().connectFail();
                        }

                        // 这块不能使用fireChannelInactive 再来手动触发  channelInactive方法了, 因为我们把Channel 已经关闭了.
                        // f.channel().pipeline().fireChannelInactive();
                        // TODO:reconnect
                        // 尝试重新连接
                        startTimerToReconnect();
                    }
                }
            });

            ChannelFuture channelFuture = future.channel().closeFuture().sync();
//            channelFuture.addListener(new ChannelFutureListener() {
//                @Override
//                public void operationComplete(ChannelFuture future) throws Exception {
//                    // 关闭会进入这里， 也会进入 channelInActive， 在channelInActive里处理就可以了。
//                    if (future.isSuccess()) {
//                        System.out.println("客户端关闭");
//                    }
//                }
//            });

        } catch (Exception e) {
            e.printStackTrace();
            connectStatus = CONNECT_STATE_FAILURE;
            if (PushClient.getInstance().getConnectStatusListener() != null) {
                PushClient.getInstance().getConnectStatusListener().connectFail();
            }
        } finally {
            // 这里不要优雅的关闭, 要重连. 走到这里, 表示在连接的过程中出现问题
            // eventLoopGroup.shutdownGracefully();
            // TODO:reconnect
            startTimerToReconnect();
        }
    }

    /**
     * 开启定时器进行重连
     */
    public void startTimerToReconnect() {

        if (!PushClient.getInstance().isReconnectNeeded()) {
            logger.warn("Cancel reconnecting with {}.", SERVER_IP + ":" + SERVER_PORT);
            return;
        }

        // 重新连接
        if (attempts < MAX_ATTEMPTS) {
            attempts++;
        }
        long t = 2 << attempts;
        logger.info("attempts=" + attempts + "    timeout=" + t);
        timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                connect();
            }
        }, t, TimeUnit.SECONDS);
    }

    /**
     * 关闭channel
     */
    private void closeChannel(Channel channel) {

        try {
            if (channel != null) {
                try {

                    System.out.println("关闭Channel, 先移除掉 Channel 中的 Handler");
                    for (ChannelHandler handler : ChannelHandlerHolder.handlers()) {
                        removeHandler(channel, handler.getClass().getSimpleName());
                    }
                    for (ChannelHandler handler : ChannelHandlerHolder.heartbeatHandlers()) {
                        removeHandler(channel, handler.getClass().getSimpleName());
                    }

                } finally {
                    try {
                        System.out.println("重连之前, 先关闭channel");
                        channel.close();
                    } catch (Exception ex) {
                    }

                    channel = null;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("重连之前, 关闭channel出错，reason:" + ex.getMessage());
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

    /**
     * 关闭连接，同时释放资源
     */
    public void close(Channel channel) {
        if (isClosed) {
            return;
        }

        isClosed = true;

        try {
            // 关闭channel
            closeChannel(channel);

            // 关闭bootstrap
            if (bootstrap != null) {
                bootstrap.group().shutdownGracefully();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            connectStatus = CONNECT_STATE_FAILURE;
            if (PushClient.getInstance().getConnectStatusListener() != null) {
                PushClient.getInstance().getConnectStatusListener().connectFail();
            }
            channel = null;
            bootstrap = null;
        }


    }

    /**
     * 是否需要重连
     *
     * @return
     */
    public boolean isReconnectNeeded() {
        // 连接失败  且  未关闭的情况下进行重连.   第一次初始化, 视为 CONNECT_STATE_FAILURE
        if (connectStatus == CONNECT_STATE_FAILURE && !isClosed) {
            return true;
        }
        return false;
    }

    public INewMessageListener getNewMessageListener() {
        return newMessageListener;
    }

    public void setNewMessageListener(INewMessageListener newMessageListener) {
        this.newMessageListener = newMessageListener;
    }

    public IConnectStatusListener getConnectStatusListener() {
        return connectStatusListener;
    }

    public void setConnectStatusListener(IConnectStatusListener connectStatusListener) {
        this.connectStatusListener = connectStatusListener;
    }
}


// Channel.closeFuture() returns a ChannelFuture that will notify you when the channel is closed. You can add a ChannelFutureListener to the future in B so that you can make another connection attempt there.
//
//You probably want to repeat this until the connection attempt succeeds finally:
//
//private void doConnect() {
//    Bootstrap b = ...;
//    b.connect().addListener((ChannelFuture f) -> {
//        if (!f.isSuccess()) {
//            long nextRetryDelay = nextRetryDelay(...);
//            f.channel().eventLoop().schedule(nextRetryDelay, ..., () -> {
//                doConnect();
//            }); // or you can give up at some point by just doing nothing.
//        }
//    });
//}
