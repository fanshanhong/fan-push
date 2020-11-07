package com.fan.push.client;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import com.fan.push.message.Message;
import com.fan.push.util.GsonUtil;
import com.fan.push.util.LoggerUtil;
import com.fan.push.util.StackTraceUtil;

public class PushClientHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
       try {
           ByteBuf byteBuf = (ByteBuf) msg;
           String msgStr = byteBuf.toString(Charset.forName("UTF-8"));

           // 需要定好编码规则, 应该统一使用UTF-8的编码
           LoggerUtil.logger.info("收到服务器的消息:" + msgStr); // Magic Socket Debugger 用UTF-8编码
           // logger.info("收到服务器的消息:" + byteBuf.toString(Charset.forName("unicode"))); // SSokit 用Unicode 编码,否则乱码

           //握手失败且返回了消息一定是服务端认证没通过 所以这里需要关闭客户端
           Message message = GsonUtil.getInstance().fromJson(msgStr, Message.class);
           if (message.getMessageType() == 1001 && message.getStatus() == -1) {
               PushClient.HANDSHAKE_SUCCESS = false;
               ctx.close();
           } else if (message.getMessageType() == 1001 && message.getStatus() == 1) {
//            ctx.pipeline().addFirst(new IdleStateHandler(20, 5, 30, TimeUnit.SECONDS));
//            ctx.pipeline().addFirst(PushClient.watchdog);
               // 这里再add IdleStateHandler才对
               // 握手成功, 开始心跳
               // TODO:这里要置一下全局的标志位,表示握手成功
               PushClient.HANDSHAKE_SUCCESS = true;
               ctx.writeAndFlush(Unpooled.wrappedBuffer(GsonUtil.getInstance().toJson(Message.obtainPingMessage()).getBytes(CharsetUtil.UTF_8))).addListener(new GenericFutureListener<Future<? super Void>>() {
                   @Override
                   public void operationComplete(Future<? super Void> future) throws Exception {
                       if (!future.isSuccess()) {
                           // 心跳发送都失败了.  重试几次.
                       }
                   }
               });
           }
       }catch (Exception e) {

           e.printStackTrace();
           System.out.println("ppppppp:=====" + e.getMessage());
       }finally {
           ctx.fireChannelRead(msg);
       }


    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        LoggerUtil.logger.info("channelRegistered");
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        LoggerUtil.logger.info("channelUnregistered");
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        LoggerUtil.logger.info("channelActive");

        new Thread(new InputScannerRunnable(ctx)).start();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LoggerUtil.logger.info("channelInactive");
        ctx.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        LoggerUtil.logger.info("userEventTriggered");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LoggerUtil.logger.info("push handler exceptionCaught");
        System.out.println(StackTraceUtil.stackTrace(cause));

        // 这里是最后一个, 不要再往后发了.
        // 如果调用super 或者 fire, 发到 tail, 就报错啦
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LoggerUtil.logger.info("handlerAdded");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        LoggerUtil.logger.info("handlerRemoved");
    }
}
