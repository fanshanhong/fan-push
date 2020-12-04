package com.fan.push.server;

import com.fan.push.message.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.netty.channel.Channel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.internal.StringUtil;


/**
 * @Description: 消息轮询器
 *
 * 思路:
 *
 * 1, 为每一个用户(userId)分配一个轮询器
 * 2, 当服务端给客户端发送消息的时候, 要把消息添加到超时管理器中
 * 3, 在 MessageRetryManager 的 add 方法中, 如果是个新的用户, 就给他分配一个轮询器, 轮询器每10秒去检查一下这个用户有没有消息需要重发
 * 4, 如果没有, 就算了. 等10秒再来看看.  如果有, 就开启定时器开始重发(是把所有没接收成功的都再发一遍).
 * 5, 如果客户端收到了, 就返回给服务器接收回执, 服务器收到接收回执, 就把消息从 超时管理器中 移除
 * 6, 如果发了5次, 客户端依然没有收到, 就认为客户端已经断线了. 此时, 将与客户端的连接断开, 并且, 将没发成功的消息写入数据库, 作为离线消息.
 * 7, 等下次客户端上线了, 统一把所有的离线消息发给客户端
 * @Author: fan
 * @Date: 2020-9-19 11:19
 * @Modify:
 */
public class MessageLooper implements TimerTask {

    // 标识这个MessageLooper 是属于哪个用户
    private String userId;

    // Netty 提供的定时器
    private Timer timer = new HashedWheelTimer();

    // 持有一个 MessageRetryManager 的引用,  因为这个轮询器就是要从 MessageRetryManager 里取数据
    private MessageRetryManager messageRetryManager;

    private String lastFirstMsgId = "";
    // 重试次数
    private int retryCount = -1;
    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 5;

    // 正在发送的, 也就是尚未接收被客户端接收到的, 也就是需要重发的  消息的List
    private List<Message> needRetryMessage = new ArrayList<>();

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


    //=========== getters =======

    public List<Message> getNeedRetryMessage() {
        if (needRetryMessage == null) {
            needRetryMessage = new ArrayList<>();
        }
        return needRetryMessage;
    }

    public String getUserId() {
        return userId;
    }

    /**
     * 开始轮询
     */
    public void loop() {
        // 10秒来检测一次, 有没有需要重发的消息
        timer.newTimeout(this, 10, TimeUnit.SECONDS);
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        System.out.println("in MessageLoop run");
        checkNeedRetry();
    }

    /**
     * 检查是否有重发消息
     */
    private void checkNeedRetry() {
        final List<Message> messages = needRetryMessage;

        if (messages != null && messages.size() > 0) {
            // 代表 userId 这个用户, 有需要重发消息, 进行重发

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
                    Channel channel = ChannelHolder.getInstance().getChannelByUserId(userId);
                    ChannelHolder.getInstance().offline(channel);

                    messageRetryManager.onUserOffline(userId);
                    channel.close();

                    // 下线了, 就不要再轮询了; onUserOffline方法中做了这些处理
                    return;

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
            // 代表 userId 这个用户, 没有消息需要重发, 继续监测即可
            loop();
        }
    }


    private void retrySendMsg() {
        System.out.println("retrySendMsg");
        // 这个方法是延时之后进入的, 因此再次确认一下这个用户是否有需要重发的消息
        List<Message> messageList = needRetryMessage;
        if (messageList == null || messageList.size() == 0) {
            // 重发队列已经为空, 不需要重发了

            // 继续监测, 正常10秒轮询就好了
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

    /**
     * 清除掉所有的消息
     */
    public void removeAllMessage() {
        needRetryMessage.clear();
    }

    /**
     * 停止轮询, 一般用于客户端掉线了
     */
    public void stopLoop() {
        timer.stop();
        timer = null;
    }
}