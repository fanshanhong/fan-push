package com.fan.push.server;

import com.fan.push.message.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息重发管理器
 * <p>
 * 客户端和服务器建立长连接之后，需要发送业务信息。由于网络不是十分稳定，连接不能保证畅通，发送消息不一定能到对方。这时候怎么办？？
 * <p>
 * <p>
 * 一种情况服务器把消息发送出去，默认这条消息客户端一定能收到，具体客户端是否真能收到，服务器不管。显然这种处理方式不对啊！！
 * <p>
 * 第二种情况服务器把消息发送出去，服务器记录这条消息的状态，客户端如果收到这条消息，向服务器发送一个回执，服务器收到这个回执将状态修改成已经收到，如果一定时间没有收到回执，则再次发送这条消息。
 * <p>
 * <p>
 * 消息就需要重发？重发几次呢？？等等一系列的问题。
 * <p>
 * 服务端维护一个正在发消息的队列，服务端向客户端推送消息后，收到客户端的回执，把响应的消息从队列中移除。服务器20s之后会轮询这个消息队列，把消息队列中已经发送的消息但是没有收到回执的消息再次发送一遍。
 * <p>
 * 如果同一条消息被发送了5次，一直没有收到回执，则认为服务器与客户端保持的这个长连接已经断开了，但是由于某种原因服务器没有把这个长连接关闭掉，这种情况服务器则把这个长连接对象channel关闭、释放掉资源。
 * <p>
 * <p>
 * 如果客户端与服务器保持的长连接对象channel关闭掉，则需要处理“正在发消息的队列”对象，将属于这个链接的消息持久化到数据库中。这样做也是为了节约内容开支，都已经判断这个长连接失效了，就没有必要再次想它推送消息了。当下次再建立这个客户端的长连接的时候我们首先在数据库中查询时候有属于它的未发送消息，如果有未发送消息，则进行发送。。
 * <p>
 * 服务器向客户端发送消息1次，客户端向服务器发送这条消息的回执。
 * <p>
 * 服务器由于网络原因没有收到回执，这条消息的回执丢了，服务器会再把这条消息发送第二次，客户端这次会收到重复的消息，这时候客户端怎么处理呢？？继续显示这条消息显然不正确，客户端需要验证这条消息是否收到，进行合法性进行验证。这里需要用到消息唯一标示。
 */
public class MessageRetryManager {

    private PushServer pushServer;

    public MessageRetryManager(PushServer pushServer) {
        this.pushServer = pushServer;
    }

    public PushServer getPushServer() {
        return pushServer;
    }

    // userId
    private ConcurrentHashMap<String, List<Message>> retryMessageMap = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, List<Message>> getRetryMessageMap() {
        return retryMessageMap;
    }

    /**
     * @param userId
     * @param message
     */
    void add(String userId, Message message) {
        if (!(message.getMessageType() == 1004)) {
            return;
        }

        // 一个新用户
        if (!retryMessageMap.containsKey(userId)) {
            retryMessageMap.put(userId, new ArrayList<Message>());
            new MessageLooper(userId, this).loop();
        }

        if (retryMessageMap.containsKey(userId) && retryMessageMap.get(userId) != null) {
            retryMessageMap.get(userId).add(message);
        }
    }

    void remove(String userId, Message message) {

        if (retryMessageMap == null || retryMessageMap.size() == 0) {
            return;
        }
        if (!retryMessageMap.containsKey(userId)) {
            return;
        }
        List<Message> messages = retryMessageMap.get(userId);
        if (messages == null || messages.size() == 0) {
            return;
        }

        messages.remove(message);

    }

    void removeUser(String userId) {
        retryMessageMap.remove(userId);
    }

    /**
     * 重连成功回调，重连并握手成功时，重发消息发送超时管理器中所有的消息
     */
    public synchronized void onReConnected(String userId) {

//        // 要从数据库里读出来
//        List<Message> messageList = getRetryMessageMap().get(userId);
//
//        // 遍历, 一起发.
//        for (Message message : messageList) {
//            getPushServer().sendMsg(userId, message, true);
//        }
    }

    public void saveAllMessageToDB(List<Message> messageList) {

    }

}
