package com.fan.push.server;

import com.fan.push.util.LoggerUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

public class HeartBeatServerHandler extends ChannelInboundHandlerAdapter {
    private PushServer pushServer;

    public HeartBeatServerHandler(PushServer pushServer) {
        this.pushServer = pushServer;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            switch (state) {
                case READER_IDLE: {
                    LoggerUtil.logger.info("客户端貌似掉线了.把客户端连接移出管理");

                    // 服务端在一段时间内没有收到客户端发来的数据包, 就认为客户端已经断线了
                    // 此时要将与客户端的连接断开, 并将连接移出管理
                    String userId = ChannelHolder.getInstance().getUserIdByChannel(ctx.channel());
                    pushServer.messageRetryManager.onUserOffline(userId);

                    ChannelHolder.getInstance().offline(ctx.channel());
                    ctx.close();
                }
                break;
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }
}