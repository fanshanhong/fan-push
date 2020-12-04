package com.fan.push.server;

import com.fan.push.client.InputScannerRunnable;
import com.fan.push.message.Message;
import com.fan.push.util.GsonUtil;
import com.fan.push.util.StackTraceUtil;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Signal;

import static com.fan.push.client.PushClient.MY_CLIENT_USER_ID;
import static com.fan.push.util.LoggerUtil.logger;

/**
 * @Description: 服务器消息处理器
 * @Author: fan
 * @Date: 2020-9-19 11:19
 * @Modify:
 */
public class PushServerHandler extends ChannelInboundHandlerAdapter {

    // 用于统计, 当前有多少客户端连接了
    private static final AtomicInteger channelCounter = new AtomicInteger(0);

    // 持有一个PushServer的引用, 方便后续发送消息等相关操作
    private PushServer pushServer;

    /**
     * constructor
     *
     * @param pushServer
     */
    public PushServerHandler(PushServer pushServer) {
        this.pushServer = pushServer;
    }

    /**
     * 当 Channel 处于活动状态时被调用；Channel 已经连接/绑定并且已经就绪
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        int count = channelCounter.incrementAndGet();

        logger.info("Connects with {} as the {}th channel.", ctx.channel(), count);

        // 开启一个线程, 用于从标准输入读取数据(作为业务数据)并发送到客户端
        new Thread(new InputScannerRunnable(ctx, true, pushServer)).start();

        super.channelActive(ctx);
    }

    /**
     * 当 Channel 离开活动状态并且不再连接它的远程节点时被调用
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 进入这里, 表示客户端已经断开连接
        // 此时要做:
        // 1, 如果发给这个用户的消息, 有未被成功收到的, 就作为离线消息存到数据库中去
        // 2, 然后把这个用户设置成离线状态
        // 3, 最后把这个连接关掉

        int count = channelCounter.getAndDecrement();

        logger.warn("Disconnects with {} as the {}th channel.", ctx.channel(), count);

        // 根据channel 拿到 客户端的userId, 然后把
        String userId = ChannelHolder.getInstance().getUserIdByChannel(ctx.channel());
        pushServer.messageRetryManager.onUserOffline(userId);

        ChannelHolder.getInstance().offline(ctx.channel());
        ctx.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();

        if (msg instanceof ByteBuf) {
            ByteBuf byteBuf = (ByteBuf) msg;

            String messageStr = byteBuf.toString(CharsetUtil.UTF_8);

            // 需要定好编码规则, 应该统一使用UTF-8的编码
            logger.info("收到客户端的消息:" + messageStr); // Magic Socket Debugger 用UTF-8编码


            Message message = GsonUtil.getInstance().fromJson(messageStr, Message.class);

            if (message == null) {
                return;
            }

            // 先判断一下消息是不是给自己的?
            if (!"server".equals(message.getTo())) {
                // 直接丢弃了, 也不需要传播
                return;
            }

            if (1001 == message.getMessageType()) {// 握手消息


                if (message.getFrom().equals(MY_CLIENT_USER_ID)) {

                    // 握手成功
                    // 先把channel 加入Map 进行管理
                    // 回复一个握手成功

                    ChannelHolder.getInstance().online(ctx.channel(), message.getFrom());

                    Message handshakeSuccessMessage = new Message(1001, "server", MY_CLIENT_USER_ID);
                    handshakeSuccessMessage.setStatus(1);
                    ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(GsonUtil.getInstance().toJson(handshakeSuccessMessage).getBytes(CharsetUtil.UTF_8)));

                    // 刚刚握手成功, 把之前所有的离线消息发送
                    pushServer.messageRetryManager.onUserOnline(message.getFrom());
                } else {
                    // 握手失败, 先将Channel 移出管理
                    ChannelHolder.getInstance().offline(ctx.channel());
                    // 发送一条握手失败的消息给客户端, 客户端就可以直接关闭自己的连接了
                    Message handshakeFailMessage = new Message(1001, "server", MY_CLIENT_USER_ID);
                    handshakeFailMessage.setStatus(-1);
                    ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(GsonUtil.getInstance().toJson(handshakeFailMessage).getBytes(CharsetUtil.UTF_8)));
                    // 服务端也关掉与客户端的连接
                    // ctx.close();
                    // 感觉这里调用 ctx.close() 会有问题吧.
                    // 服务端直接调用ctx.close(),  客户端是不是就感知到了? 客户端就直接进入到 channelInactive 中了?
                    // 这样的话, 客户端应该就收不到 握手失败这个消息了呢?
                    // 所以这里不要 ctx.close() 了. 让客户端自己断开连接好了. 客户端断开连接之后, 服务端能感知到(进入channelInactive), 服务端再处理
                }

            } else if (1002 == message.getMessageType()) { // ping 消息
                Message pongMessage = Message.obtainPongMessage();
                pongMessage.setTo(message.getFrom());
                ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(GsonUtil.getInstance().toJson(pongMessage).getBytes(CharsetUtil.UTF_8)));
            } else if (1003 == message.getMessageType()) {
                // 服务端不会收到pong消息
            } else if (1004 == message.getMessageType()) {
                if (message.getStatus() == 1) { // 客户端正常收到消息, 发来回执
                    if (pushServer != null) {
                        pushServer.removeMsgFromRetryManager(message.getFrom(), message);
                    }
                }
            }
        } else {
            logger.warn("Unexpected message type received: {}, channel: {}.", msg.getClass(), ch);
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        ChannelConfig config = ch.config();

        // 高水位线: ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK
        // 低水位线: ChannelOption.WRITE_BUFFER_LOW_WATER_MARK
        if (!ch.isWritable()) {
            // 当前channel的缓冲区(OutboundBuffer)大小超过了WRITE_BUFFER_HIGH_WATER_MARK
            if (logger.isWarnEnabled()) {
                logger.warn("{} is not writable, high water mask: {}, the number of flushed entries that are not written yet: {}.",
                        ch, config.getWriteBufferHighWaterMark(), ch.unsafe().outboundBuffer().size());
            }

            config.setAutoRead(false);
        } else {
            // 曾经高于高水位线的OutboundBuffer现在已经低于WRITE_BUFFER_LOW_WATER_MARK了
            if (logger.isWarnEnabled()) {
                logger.warn("{} is writable(rehabilitate), low water mask: {}, the number of flushed entries that are not written yet: {}.",
                        ch, config.getWriteBufferLowWaterMark(), ch.unsafe().outboundBuffer().size());
            }

            config.setAutoRead(true);
        }
    }

    /**
     * 异常处理
     * 我们的 PushServerHandler 是 pipeline 中的最后一个 Handler 了, 不要再往后发了, 就是不要再去调用 ctx.fireExceptionCaught() 了
     * 如果调用 super 或者 fire, 发到 pipeline 中的 tail, 就报错啦
     *
     * TailContext 中的  exceptionCaught 如下:
     *
     * @param ctx
     * @param cause
     * @throws Exception
     */
    //    /**
    //    * Called once a {@link Throwable} hit the end of the {@link ChannelPipeline} without been handled by the user
    //    * in {@link ChannelHandler#exceptionCaught(ChannelHandlerContext, Throwable)}.
    //    */
    //    protected void onUnhandledInboundException(Throwable cause) {
    //        try {
    //            logger.warn(
    //                    "An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " +
    //                            "It usually means the last handler in the pipeline did not handle the exception.",
    //                    cause);
    //        } finally {
    //            ReferenceCountUtil.release(cause);
    //        }
    //    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel ch = ctx.channel();

        if (cause instanceof Signal) {
            logger.error("I/O signal was caught: {}, force to close channel: {}.", ((Signal) cause).name(), ch);

            ch.close();
        } else if (cause instanceof IOException) {
            logger.error("An I/O exception was caught: {}, force to close channel: {}.", StackTraceUtil.stackTrace(cause), ch);

            ch.close();
        } else if (cause instanceof DecoderException) {
            logger.error("Decoder exception was caught: {}, force to close channel: {}.", StackTraceUtil.stackTrace(cause), ch);

            ch.close();
        } else {
            logger.error("Unexpected exception was caught: {}, channel: {}.", StackTraceUtil.stackTrace(cause), ch);
        }
    }
}
