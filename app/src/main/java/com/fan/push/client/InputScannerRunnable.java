package com.fan.push.client;

import com.fan.push.message.Message;
import com.fan.push.server.ChannelHolder;
import com.fan.push.server.PushServer;

import java.util.Scanner;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;

public class InputScannerRunnable implements Runnable {

    private ChannelHandlerContext ctx;

    // 是否是服务器, 客户端/服务器 要区别处理
    private boolean isServer;

    private PushServer pushServer;

    public InputScannerRunnable(ChannelHandlerContext ctx, boolean isServer, PushServer pushServer) {
        this.ctx = ctx;
        this.isServer = isServer;
        this.pushServer = pushServer;
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {

            // 从终端读入数据, 将数据封装成一个Message
            String line = scanner.nextLine();

            // 关闭连接
            if (line.equalsIgnoreCase("##stop")) {
                System.out.println("input end!!");

                // 服务端
                if (isServer) {

                    // TODO: 要关闭 与客户端的连接了, 如果还有客户端未收到的消息, 就要存在数据库了
                    String userId = ChannelHolder.getInstance().getUserIdByChannel(ctx.channel());
                    pushServer.messageRetryManager.onUserOffline(userId);

                    ChannelHolder.getInstance().offline(ctx.channel());
                    ctx.close();
                } else {// 客户端
                    // 这块如果关闭了Channel, 想要再发送, 就需要重新建立连接了
                    PushClient.getInstance().close(ctx.channel());
                }
                return;
            }

            Message message = new Message(1004, "server", PushClient.MY_CLIENT_USER_ID);
            message.setContent(line);

            if (isServer) {
                // 找到是发给谁的
                String to = message.getTo();

                if (!StringUtil.isNullOrEmpty(to)) {
                    if (pushServer != null) {
                        pushServer.sendMsg(to, message, true);
                    }
                }
            } else {
                ctx.writeAndFlush(Unpooled.copiedBuffer(line.getBytes(CharsetUtil.UTF_8)));
            }
        }
    }
}
