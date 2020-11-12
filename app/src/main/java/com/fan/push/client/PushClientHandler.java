package com.fan.push.client;

import java.nio.charset.Charset;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

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

    // 用于防止消息重复接收
    // 服务器向客户端发送消息1次，客户端向服务器发送这条消息的回执。
    // 服务器由于网络原因没有收到回执，这条消息的回执丢了，服务器会再把这条消息发送第二次，客户端这次会收到重复的消息，这时候客户端怎么处理呢？？
    // 继续显示这条消息显然不正确，客户端需要验证这条消息是否收到，进行合法性进行验证。这里需要用到消息唯一标示(messageId)。
    // 我们使用一个大的 队列, 里面专门存放已经收到的消息的 id
    // 收到消息后, 如果queue 里没有, 就入队, 正常处理
    //           如果queue 里有了, 就代表是重复的消息了, 此时, 只要告诉服务器我们收到了就行了, 然后把消息丢弃, 不做处理
    // 为啥还要告诉服务器呢?  因为你不给服务器发已收到回执, 这条消息就一直存在服务器的超时管理器里, 会一直给客户端重发

    // 但是这个方案有个问题:

    // 比如, 此时, 服务器给客户端发了一个消息A, 客户端收到了. 但是客户端发送的回执服务器没收到. 客户端就下线了. 那, 这条消息A就作为离线消息存起来了.
    // 下次客户端再上线, 服务器会把离线消息A再次发来.
    // 此时, 客户端上线, PushClientHandler 对象是新建的, 那么 oldMessageQueue 集合就是空的了. 导致的问题就是: 客户端会认为A 并不是一条重复的消息, 会显示出来. 其实, 消息A在客户端上次下线之前就已经处理过了
    // 那怎么做呢?
    // 要把收到的消息持久化吧?
    // 我们自己弄个数据库, 里面用于存历史收到的消息的messageId. 这样就可以了.
    // 同时要考虑到, 这个数据库的记录数量不能无限增长. 因为一直收消息一直收, 就爆炸了
    // 可以设置数据库记录最大10万条. 多了之后, 就把最老的记录覆盖, 这样是不是就可以了.
    private Queue<String> oldMessageQueue = new ArrayBlockingQueue(4096);

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

                // 集合已经包含这一条消息的messageId了, 认为是重复消息, 只发回执不处理
                if (oldMessageQueue.contains(message.getMessageId())) {
                    // 构造一条接收回执消息
                    Message reportBackMessage = new Message(1004, PushClient.MY_CLIENT_USER_ID, "server");
                    reportBackMessage.setStatus(1);
                    ctx.writeAndFlush(Unpooled.wrappedBuffer(GsonUtil.getInstance().toJson(reportBackMessage).getBytes(CharsetUtil.UTF_8)));
                    return;
                }

                oldMessageQueue.add(message.getMessageId());
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

                // TODO:自己处理这条消息
                System.out.println("服务器说:" + message.getContent());
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
