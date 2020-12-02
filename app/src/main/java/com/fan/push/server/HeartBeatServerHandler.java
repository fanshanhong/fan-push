package com.fan.push.server;

import com.fan.push.util.LoggerUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * @Description: 用于处理服务端心跳的处理器
 * @Author: fan
 * @Date: 2020-9-19 11:19
 * @Modify:
 */
public class HeartBeatServerHandler extends ChannelInboundHandlerAdapter {

    private PushServer pushServer;

    /**
     * constructor
     *
     * @param pushServer
     */
    public HeartBeatServerHandler(PushServer pushServer) {
        this.pushServer = pushServer;
    }

    /**
     * 配合 IdleStateHandler 使用
     * 当读/写超时, 会触发 userEventTriggered
     *
     * @param ctx
     * @param evt
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            switch (state) {
                case READER_IDLE: {// 读超时
                    LoggerUtil.logger.info("客户端貌似掉线了.把客户端连接移出管理");

                    // 服务端在一段时间内没有收到客户端发来的数据包, 就触发读超时, 此时认为客户端已经断线了
                    // 此时要:
                    // 1,如果超时管理器中有这个客户端未接收成功的消息, 作为离线消息存储
                    // 2,将连接移出管理
                    // 3,将与客户端的连接断开

                    // step1:
                    String userId = ChannelHolder.getInstance().getUserIdByChannel(ctx.channel());
                    pushServer.messageRetryManager.onUserOffline(userId);

                    // step2:
                    ChannelHolder.getInstance().offline(ctx.channel());

                    // step3:
                    ctx.close();
                }
                break;
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }
}