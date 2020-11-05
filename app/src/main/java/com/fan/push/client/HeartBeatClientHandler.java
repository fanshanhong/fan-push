package com.fan.push.client;

import com.fan.push.message.Message;
import com.fan.push.util.GsonUtil;
import com.fan.push.util.LoggerUtil;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;

public class HeartBeatClientHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            switch (state) {
                case READER_IDLE: {
                    // 规定时间内没收到服务端心跳包响应，进行重连操作
                    // TODO: reconnect
                    LoggerUtil.logger.info("规定时间内没收到服务端心跳包响应，进行重连操作");

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
}
