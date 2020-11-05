package com.fan.push.client;

import com.fan.push.util.LoggerUtil;

import java.util.Scanner;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;

public class InputScannerRunnable implements Runnable {

    private ChannelHandlerContext ctx;

    public InputScannerRunnable(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.equalsIgnoreCase("##stop")) {
                LoggerUtil.logger.info("input end!!");
                // 这块如果关闭了Channel, 想要再发送, 就需要重新建立连接了
                ctx.close();
                return;
            }
            ctx.writeAndFlush(Unpooled.copiedBuffer(line.getBytes(CharsetUtil.UTF_8)));
        }
    }
}
