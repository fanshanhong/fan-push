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
                    // 为啥  又要监听  读超时, 又要在  WatchDog的 ChannelInActive 中重连?
                    // 因为, 如果是服务器把客户端的连接关闭了, 只进入  WatchDog的 ChannelInActive, 不会再进入这里
                    //      如果是服务器断网,客户端断网之类的情况,客户端不会进入 inActive, 只能通过这里的 读超时来判断
                    // 注意: 手机端断开WIFI,或者开飞行模式, 都是会触发 ChannelInActive的
                    // 这里主要是用于网络中间节点断了这种情况.
                    PushClient.connectStatus = PushClient.CONNECT_STATE_FAILURE;
                    LoggerUtil.logger.info("PushClient.connectState=" + PushClient.connectStatus + "  PushClient.isClosed=" + PushClient.isClosed);

                    if (!PushClient.getInstance().isReconnectNeeded()) {
                        LoggerUtil.logger.info("好像正在连接? 或者没握手成功(客户端关闭了)? 就不需要重连了");
                        return;
                    }

                    LoggerUtil.logger.info("规定时间内没收到服务端心跳包响应，进行重连操作");

                    // TODO: reconnect
                    PushClient.getInstance().startNewTimerToReconnect();
                }
                break;

                case WRITER_IDLE: {
                    // 规定时间内没有过写操作,向服务器发送一个心跳包
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
}