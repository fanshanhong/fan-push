package com.fan.push.server;

import com.fan.push.message.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * TIP:
 *
 *
 * 该类不再使用, 只备份一下!!!
 * 使用 MessageRetryManager
 *
 *
 * 消息重发管理器
 * <p>
 * 客户端和服务器建立长连接之后，需要发送业务信息。由于网络等各种原因，连接不能保证畅通，发送消息不一定能到对方。
 * <p>
 * 服务器把消息发送出去，服务器记录这条消息的状态，客户端如果收到这条消息，向服务器发送一个已收到的回执，服务器收到这个回执将状态修改成已经收到，如果一定时间没有收到回执，则再次发送这条消息。
 * <p>
 * 服务端维护一个正在发消息的队列，每次发送消息的同时, 把消息往正在发送的消息队列中存一分. 当收到客户端的回执，再把相应的消息从队列中移除。
 * 服务器每10s会轮询这个消息队列，把消息队列中已经发送的消息但是没有收到回执的消息再次发送一遍。
 * <p>
 * 如果同一条消息被发送了5次，一直没有收到回执，则认为服务器与客户端保持的这个长连接已经断开了，这种情况服务器则把这个长连接对象channel关闭、释放掉资源。
 * <p>
 * <p>
 * 如果客户端与服务器保持的长连接对象channel关闭掉，则需要处理“正在发消息的队列”中的消息，将属于这个channel的消息持久化到数据库中。
 * 当下次再建立这个客户端的长连接的时候我们首先在数据库中查询时候有属于它的未发送消息，如果有未发送消息，则进行发送。
 * <p>
 * <p>
 * 另一个问题:
 * 服务器向客户端发送消息1次，客户端向服务器发送这条消息的回执。
 * 服务器由于网络原因没有收到回执，这条消息的回执丢了，服务器会再把这条消息发送第二次，客户端这次会收到重复的消息，这时候客户端怎么处理呢？？继续显示这条消息显然不正确，客户端需要验证这条消息是否收到，进行合法性进行验证。这里需要用到消息唯一标示(messageId)。
 * <p>
 * 目前的想法:客户端维护一个大一点的Set,里面存放 收到的 类型为1004的消息的messageId.
 * 当有消息来, 先判断Set里有没有这个messageId, 如果有, 就代表最近已经收到过了, 直接丢弃这条消息了
 */
public class MessageRetryManager1 {

    private ExecutorService executorService = Executors.newCachedThreadPool();

    private PushServer pushServer;

    public MessageRetryManager1(PushServer pushServer) {
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
            //new MessageLooper(userId, this).loop();
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

    public void removeUser(String userId) {
        retryMessageMap.remove(userId);
    }

    /**
     * 重连成功回调，重连并握手成功时，重发消息发送超时管理器中所有的消息
     */
    public synchronized void onUserOnline(String userId) {

        // 要从数据库里读出来
        List<Message> messageList = loadAllOfflineMessageFromDB(userId);

        // 遍历, 一起发.
        for (Message message : messageList) {
            getPushServer().sendMsg(userId, message, true);
        }
    }

    private List<Message> loadAllOfflineMessageFromDB(String userId) {

        // TODO:从数据库查询出这个用户的所有离线消息, 放在超时管理器中, 然后要把离线消息从数据库表中移除了
        List<Message> messageList = new ArrayList<>();
        messageList.addAll(mookDBMessageList);
        mookDBMessageList.clear();

        return messageList;
    }


    public synchronized void onUserOffline(final String userId) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                // 把userId 对应的全部消息都写入数据库, 等上线了再一起发
                //pushServer.messageRetryManager.saveOfflineMessage(userId);

                // 然后把这个userId 从map中移除
                // pushServer.messageRetryManager.removeUser(userId);
            }
        });
    }

    /**
     * 存储离线消息
     *
     * @param userId
     */
    private void saveOfflineMessage(String userId) {
        List<Message> messageList = retryMessageMap.get(userId);


        if (messageList == null || messageList.size() == 0) {
            // 没有需要存储的离线消息
            return;
        }

        // 数据库里有, 就更新, 没有就插入
        saveMessageToDB(messageList);
    }

    private void saveMessageToDB(List<Message> messageList) {

        // TODO:批量更新和插入
        for (Message message : messageList) {
            if (mookDBMessageList.contains(message)) { // 包含, 就代表数据存在这个id的 消息, 需要更新
                // 这里contains 比较的只是messageId 和 messageType
                // 除了这两个字段, 其他字段可能不同, 因此先remove 一下, 再add一下, 就相当于是数据的更新了
                int index = mookDBMessageList.indexOf(message);
                mookDBMessageList.remove(index);
                mookDBMessageList.add(index, message);
            } else { // 插入
                mookDBMessageList.add(message);
            }
        }
    }

    // 用于模拟数据库的集合
    private List<Message> mookDBMessageList = new ArrayList<>();

}
