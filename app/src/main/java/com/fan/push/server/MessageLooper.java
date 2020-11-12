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

public class MessageLooper implements TimerTask {

    private String userId;
    private Timer timer = new HashedWheelTimer();
    private MessageRetryManager messageRetryManager;

    private String lastFirstMsgId = "";
    private int retryCount = -1;
    private static final int MAX_RETRY_COUNT = 5;

    /**
     * const
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

            if (lastFirstMsgId.equals(currentFirstMsgId)) { // 之前的没成功
                if (retryCount < MAX_RETRY_COUNT) {
                    retryCount++;
                } else {
                    // 5次都没成功, 认为客户端掉线了
                    Channel channelByUserId = ChannelHolder.getInstance().getChannelByUserId(userId);
                    ChannelHolder.getInstance().offline(channelByUserId);

                    messageRetryManager.onUserOffline(userId);
                    channelByUserId.close();
                }
            } else { // lastFirstMsgId 已经被客户端成功接收了
                lastFirstMsgId = currentFirstMsgId;
                retryCount = 0;
            }
            int delay = retryCount * 4;//第一次是0,  0*4=0
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
        // 再判断一下这个用户是否有需要重发的消息
        List<Message> messageList = messageRetryManager.getRetryMessageMap().get(userId);
        if (messageList == null || messageList.size() == 0) {
            // 重发队列已经为空, 不需要重发了

            // 正常10秒轮询就好了
            // 继续监测
            loop();
            return;
        }

        lastFirstMsgId = messageList.get(0).getMessageId();
        // 确实要发
        for (Message message : messageList) {
            messageRetryManager.getPushServer().sendMsg(userId, message, false);
        }
        checkNeedRetry();
    }
}