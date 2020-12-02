package com.fan.push.server;

import com.fan.push.message.Message;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netty.util.internal.StringUtil;

/**
 * @Description: 消息重发管理器
 *
 * 思路:
 *
 * 客户端和服务器建立长连接之后，需要发送业务信息。由于网络等各种原因，连接不能保证畅通，发送的消息不一定能到对方。
 *
 * 服务器把消息发送出去，服务器记录这条消息的状态:
 * 客户端如果收到这条消息，向服务器发送一个已收到的回执，服务器收到这个回执后将状态修改成已经收到; 如果一定时间没有收到回执，则再次发送这条消息。
 *
 * 服务端维护一个正在发消息的队列，每次发送消息的同时, 把消息往正在发送的消息队列中存一份. 当收到客户端的回执，再把相应的消息从队列中移除。
 * 服务器每10s会轮询这个消息队列，把消息队列中已经发送的消息但是没有收到回执的消息再次发送一遍。
 *
 * 如果同一条消息被发送了5次，一直没有收到回执，则认为服务器与客户端保持的这个长连接已经断开了，这种情况服务器则把这个长连接对象channel关闭、释放掉资源。
 *
 *
 * 如果客户端与服务器保持的长连接对象channel关闭掉，则需要处理“正在发消息的队列”中的消息，将属于这个channel的消息持久化到数据库中。
 * 当下次这个客户端用户再次上线, 也就是再次建立客户端的长连接的时候, 我们首先在数据库中查询时候有属于它的未发送消息，全部进行发送。
 *
 *
 * 另一个问题:
 * 服务器向客户端发送消息1次，客户端向服务器发送这条消息的回执。
 * 服务器由于网络原因没有收到回执，这条消息的回执丢了，服务器会再把这条消息发送第二次，客户端这次会收到重复的消息，这时候客户端怎么处理呢？？继续显示这条消息显然不正确，客户端需要验证这条消息是否收到!
 * 进行合法性进行验证。这里需要用到消息唯一标示(messageId)。
 *
 * 具体客户端如何进行消息重复性验证, 参考 PushClientHandler
 * @Author: fan
 * @Date: 2020-9-19 11:19
 * @Modify:
 */
public class MessageRetryManager {

    // 用于数据库读写的线程池
    private ExecutorService executorService = Executors.newCachedThreadPool();

    // 持有一个 PushServer的引用, 用于后续发送消息方便.
    // 因为在PushServer 中有 sendMsg() 等相关便捷方法
    private PushServer pushServer;

    //  Map 用于维护  userId  <---->  MessageLooper  的映射关系, 也就是说, 给一个userId 分配一个单独的 MessageLooper(消息轮询器)
    //  同时, 正在发送的消息 List 存放在 MessageLooper 中, 让 MessageLooper 去轮询这个消息List.
    private ConcurrentHashMap<String, MessageLooper> looperMap = new ConcurrentHashMap<>();

    /**
     * constructor
     *
     * @param pushServer
     */
    public MessageRetryManager(PushServer pushServer) {
        this.pushServer = pushServer;
    }

    public PushServer getPushServer() {
        return pushServer;
    }

    /**
     * 将一条消息加入到消息重发管理器中
     *
     * @param userId
     * @param message
     */
    public void add(String userId, Message message) {

        if (StringUtil.isNullOrEmpty(userId)) {
            return;
        }

        // 消息重发管理器只管理业务消息(也就是 messageType=1004的, 其他的 pong 消息, 握手消息等不考虑)
        if (!(message.getMessageType() == 1004)) {
            return;
        }

        if (looperMap == null) {
            looperMap = new ConcurrentHashMap<>();
        }

        // 判断如果是一个新用户, 则为他单独分配一个消息轮询器
        if (!looperMap.containsKey(userId)) {

            MessageLooper messageLooper = new MessageLooper(userId, this);
            looperMap.put(userId, messageLooper);

            // 记得调用一下loop()方法开始轮询
            messageLooper.loop();
        }

        MessageLooper messageLooper = looperMap.get(userId);
        if (messageLooper == null) {
            return;
        }

        if (messageLooper.getNeedRetryMessage() == null) {
            return;
        }

        messageLooper.getNeedRetryMessage().add(message);
    }


    /**
     * 将一条消息从重发管理器中移除
     *
     * @param userId
     * @param message
     */
    public void remove(String userId, Message message) {

        if (StringUtil.isNullOrEmpty(userId)) {
            return;
        }

        if (looperMap == null || looperMap.size() == 0) {
            return;
        }
        if (!looperMap.containsKey(userId)) {
            return;
        }

        MessageLooper messageLooper = looperMap.get(userId);

        if (messageLooper == null) {
            return;
        }

        if (messageLooper.getNeedRetryMessage() == null) {
            return;
        }
        messageLooper.getNeedRetryMessage().remove(message);
    }

    /**
     * 将整个用户的消息全部从重发管理器中移除
     *
     * @param userId
     */
    public void removeUser(String userId) {

        if (StringUtil.isNullOrEmpty(userId)) {
            return;
        }

        if (looperMap == null || looperMap.size() == 0) {
            return;
        }
        if (!looperMap.containsKey(userId)) {
            return;
        }

        MessageLooper messageLooper = looperMap.get(userId);

        if (messageLooper == null) {
            return;
        }

        messageLooper.removeAllMessage();
        messageLooper.stopLoop();
        looperMap.remove(userId);
    }

    /**
     * 重连成功回调，重连并握手成功时，重发离线消息
     *
     * @param userId
     */
    public synchronized void onUserOnline(String userId) {

        // 要从数据库里读出来原先发送给这个userId的所有离线消息
        List<Message> messageList = loadAllOfflineMessageFromDB(userId);

        // 遍历, 一起发.
        for (Message message : messageList) {
            // 注意这里的第三个参数: addToRetryManager 是 true. 也就是这次发送, 也要加入到重发管理器里的. 防止消息丢失
            pushServer.sendMsg(userId, message, true);
        }
    }

    /**
     * 根据 userId 从数据库中获取所有的离线消息
     *
     * @param userId
     * @return
     */
    private List<Message> loadAllOfflineMessageFromDB(String userId) {

        // TODO:从数据库查询出这个用户的所有离线消息, 放在超时管理器中, 然后要把离线消息从数据库表中移除了
        // 这里用一个List 来模拟数据库
        List<Message> messageList = new ArrayList<>();
        messageList.addAll(mookDBMessageList);
        mookDBMessageList.clear();

        return messageList;
    }


    /**
     * 当用户离线
     *
     * @param userId
     */
    public synchronized void onUserOffline(final String userId) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                // 把 userId 对应的全部消息都写入数据库, 等上线了再一起发
                saveOfflineMessage(userId);

                // 然后把这个userId 从map中移除
                removeUser(userId);
            }
        });
    }

    /**
     * 存储离线消息
     *
     * @param userId
     */
    private void saveOfflineMessage(String userId) {

        if (StringUtil.isNullOrEmpty(userId)) {
            return;
        }

        if (looperMap == null || looperMap.size() == 0) {
            return;
        }
        if (!looperMap.containsKey(userId)) {
            return;
        }

        MessageLooper messageLooper = looperMap.get(userId);
        if (messageLooper == null) {
            return;
        }
        List<Message> needRetryMessage = messageLooper.getNeedRetryMessage();
        if (needRetryMessage == null || needRetryMessage.isEmpty()) {
            // 没有需要存储的离线消息
            return;
        }

        // 将轮询器中的需要重发的消息全部持久化到数据库中
        // 数据库里有, 就更新, 没有就插入
        saveMessageToDB(needRetryMessage);
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
