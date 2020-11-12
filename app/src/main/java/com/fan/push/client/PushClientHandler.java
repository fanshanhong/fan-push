package com.fan.push.client;

import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

import com.fan.push.message.Message;
import com.fan.push.util.GsonUtil;
import com.fan.push.util.LoggerUtil;
import com.fan.push.util.StackTraceUtil;


/**
 *
 */
public class PushClientHandler extends ChannelInboundHandlerAdapter {

    private int count = 0;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            ByteBuf byteBuf = (ByteBuf) msg;
            String msgStr = byteBuf.toString(Charset.forName("UTF-8"));

            // 需要定好编码规则, 应该统一使用UTF-8的编码
            LoggerUtil.logger.info("收到服务器的消息:" + msgStr); // Magic Socket Debugger 用UTF-8编码
            // logger.info("收到服务器的消息:" + byteBuf.toString(Charset.forName("unicode"))); // SSokit 用Unicode 编码,否则乱码

            //握手失败且返回了消息一定是服务端认证没通过 所以这里需要关闭客户端, 也不需要重连, 因为账号密码都错了!
            Message message = GsonUtil.getInstance().fromJson(msgStr, Message.class);

            if (message == null) {
                return;
            }

            // 看一下是不是给自己的消息?
            if (!PushClient.MY_CLIENT_USER_ID.equals(message.getTo())) {
                return;
            }

            if (message.getMessageType() == 1001 && message.getStatus() == -1) {
                // 握手失败
                PushClient.getInstance().close(ctx.channel());
            } else if (message.getMessageType() == 1001 && message.getStatus() == 1) {
                // 握手成功, 开始心跳, 此时再add IdleStateHandler才对
                for (ChannelHandler handler : ChannelHandlerHolder.heartbeatHandlers()) {
                    ctx.pipeline().addFirst(handler.getClass().getSimpleName(), handler);
                }

                // 主动先发一条心跳数据包给服务端
                Message pingMessage = Message.obtainPingMessage();
                pingMessage.setFrom(PushClient.MY_CLIENT_USER_ID);
                ctx.writeAndFlush(Unpooled.wrappedBuffer(GsonUtil.getInstance().toJson(pingMessage).getBytes(CharsetUtil.UTF_8)));
            } else if (message.getMessageType() == 1004) {
                count++;
                // 构造一条接收回执消息
                Message reportBackMessage = new Message(1004, PushClient.MY_CLIENT_USER_ID, "server");
                reportBackMessage.setStatus(1);

                // 想要测试消息重发, 这块注释即可
                // 为了测试.  第一条消息, 发送回执, 第二条消息, 不发回执, 看看情况
                //System.out.println("count:" + count);
                //if(count%2 == 1) {
                // 发送接收回执
                ctx.writeAndFlush(Unpooled.wrappedBuffer(GsonUtil.getInstance().toJson(reportBackMessage).getBytes(CharsetUtil.UTF_8)));
                //}
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channelRegistered:::" + ctx.channel().id().asLongText());
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        LoggerUtil.logger.info("channelUnregistered");
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        LoggerUtil.logger.info("channelActive");
        // 开启一个线程, 不断从终端输入读取, 并发送到服务端
        //new Thread(new InputScannerRunnable(ctx, false)).start();
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LoggerUtil.logger.info("channelInactive");
        ctx.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        LoggerUtil.logger.info("userEventTriggered");
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LoggerUtil.logger.info("push handler exceptionCaught: " + StackTraceUtil.stackTrace(cause));

        // 这里是最后一个, 不要再往后发了.
        // 如果调用super 或者 fire, 发到 tail, 就报错啦
        ctx.close();
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
