package com.fan.push.server;

import com.fan.push.message.Message;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.netty.channel.Channel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.internal.StringUtil;


/**
 * 消息轮询器
 * <p>
 * <p>
 * 思路:
 * 1, 为每一个用户(userId)分配一个轮询器
 * 2, 当服务端给客户端发送消息的时候, 要把消息添加到超时管理器中
 * 3, 在 MessageRetryManager 的 add 方法中, 如果是个新的用户, 就给他分配一个轮询器, 轮询器每10秒去检查一下这个用户有没有消息需要重发
 * 4, 如果没有, 就拉到. 等10秒再来看看.  如果有, 就开启定时器开始重发(是把所有没接收成功的都再发一遍).
 * 5, 如果客户端收到了, 就返回给服务器接收回执, 服务器收到接收回执, 就把消息从 超时管理器中 移除
 * 6, 如果发了5次, 客户端依然没有收到, 就认为客户端已经断线了. 此时, 将与客户端的连接断开, 并且, 将没发成功的消息写入数据库, 作为离线消息.
 * 7, 等下次客户端上线了, 统一把所有的离线消息发给客户端
 */
public class MessageLooper implements TimerTask {

    private String userId;
    private Timer timer = new HashedWheelTimer();
    private MessageRetryManager messageRetryManager;

    private String lastFirstMsgId = "";
    private int retryCount = -1;
    private static final int MAX_RETRY_COUNT = 5;

    /**
     * constructor
     *
     * @param userId
     */
    public MessageLooper(String userId, MessageRetryManager messageRetryManager) {
        if (StringUtil.isNullOrEmpty(userId)) {
            throw new IllegalArgumentException("MessageLooper constructor , userId can not be null");
        }
        if (messageRetryManager == null) {
            throw new IllegalArgumentException("MessageLooper constructor , messageRetryManager can not be null");
        }
        this.userId = userId;
        this.messageRetryManager = messageRetryManager;
    }

    public void loop() {
        // 10秒来检测一次, 有没有需要重发的消息
        timer.newTimeout(this, 10, TimeUnit.SECONDS);
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        System.out.println("in MessageLoop run");
    }

    private void checkNeedRetry() {
        System.out.println("in checkNeedRetry()");
        // 这个userId 有没有需要重发的
        final List<Message> messages = messageRetryManager.getRetryMessageMap().get(userId);
        System.out.println(messages);

        if (messages != null && messages.size() > 0) {
            // 代表userId 这个用户, 有需要重发消息, 进行重发

            // 第一条消息
            final Message currentFirstMessage = messages.get(0);
            // 目前的第一条消息id
            final String currentFirstMsgId = currentFirstMessage.getMessageId();

            System.out.println("last: " + lastFirstMsgId + "   current:" + currentFirstMsgId + "  retryCount:" + retryCount);


            // 这里通过 lastFirstMsgId 来判断之前的第一条消息是否被成功接收了
            // 如果 lastFirstMsgId 被接收成功的话, 就从超时管理器里移除了
            // lastFirstMsgId.equals(currentFirstMsgId) 代表之前的没发成功, 这个last消息还存在与超时管理器中

            if (lastFirstMsgId.equals(currentFirstMsgId)) {
                // 之前的重发没成功, 加长延时, 再次重发
                if (retryCount < MAX_RETRY_COUNT) {
                    retryCount++;
                } else {
                    // 5次都没成功, 认为客户端掉线了
                    Channel channelByUserId = ChannelHolder.getInstance().getChannelByUserId(userId);
                    ChannelHolder.getInstance().offline(channelByUserId);

                    messageRetryManager.onUserOffline(userId);
                    channelByUserId.close();
                }
            } else { // lastFirstMsgId 已经被客户端成功接收了, 那就更新 last, 并把计数清零
                lastFirstMsgId = currentFirstMsgId;
                retryCount = 0;
            }
            int delay = retryCount * 4;//第一次是0,  0*4=0, 也就是说第一次不用延时的.  因为第一次是10秒轮询, 不需要延时
            System.out.println("delay:" + delay);
            timer.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    retrySendMsg();
                }
            }, delay, TimeUnit.SECONDS);
        } else {
            // 没, 继续监测
            loop();
        }
    }


    private void retrySendMsg() {
        System.out.println("retrySendMsg");
        // 这个方法是延时之后进入的, 因此再次确认一下这个用户是否有需要重发的消息
        List<Message> messageList = messageRetryManager.getRetryMessageMap().get(userId);
        if (messageList == null || messageList.size() == 0) {
            // 重发队列已经为空, 不需要重发了

            // 正常10秒轮询就好了
            // 继续监测
            loop();
            return;
        }

        // 这个方法是延时之后进入的, 在延时期间, 超时管理器中的数据可能有变化(有可能有新发的消息, 也有可能有客户端收到消息后,从超时管理器中移除消息了)
        // 因此, 这里再次更新一下 lastFirstMsgId
        lastFirstMsgId = messageList.get(0).getMessageId();
        // 确实有消息需要重发
        for (Message message : messageList) {
            messageRetryManager.getPushServer().sendMsg(userId, message, false);
        }

        // 发完, 休息一下, 给客户端回执留出时间
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        checkNeedRetry();
    }
}