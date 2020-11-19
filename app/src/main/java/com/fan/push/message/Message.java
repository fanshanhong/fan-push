package com.fan.push.message;

import java.util.Objects;
import java.util.UUID;

/**
 * 消息封装
 *
 *
 * 按照流程顺序, 梳理一下, 我们一共需要这几种类型的消息:
 *
 *
 * 0, 服务器运行
 *
 * 1, 最开始客户端连接后, 客户端需要给服务端发送一个握手消息(1001) 所需字段:  messageType=1001 from=客户端userId timestamp
 *
 * 2, 服务端收到客户端的握手消息后, 验证
 * a, 如果验证成功, 返回给客户端握手成功的消息:  messageType=1001 status=1  from=server to=客户端userId timestamp
 * b, 如果验证失败, 返回给客户端握手失败的消息  messageType=1001 status=-1 from=server to=客户端userId timestamp
 *
 * 3, 客户端端收到握手消息
 * a, 握手消息(1001) status=-1, 就知道自己握手都没成功, 直接关闭连接
 * b, 握手消息(1001) status=1,  就知道自己握手成功, 开始后续的逻辑:心跳
 *
 *
 * 4, 客户端发送的心跳消息(ping),   messageType=1002, from=客户端userId, to=server, timestamp
 *
 * 5, 服务端接收到心跳消息(ping)后, 返回心跳响应消息(pong),  messageType=1003, from=server, to=客户端userId, timestamp
 *
 * 下面进行正常业务推送
 *
 * 6, 服务端给客户端推送消息(1004), messageType=1004, messageId, content, from=server, to=客户端userId, timestamp
 *
 * 7, 如果客户端收到服务器的推送消息, 发送收到回执. messageType=1004, messageId, from=客户端userId, to=server, status=1, timestamp
 *
 *
 * Next,
 * 1, 按照这个消息规则调整
 * 2, 完成离线消息功能
 */
public class Message {

    // 消息类型   1001 握手  1002 ping  1003 pong   1004 消息
    private int messageType;
    // 消息的唯一标识码
    private String messageId;
    // 消息内容
    private String content;

    // 消息状态.
    // 主要用于  1001握手响应  和  1004消息的客户端收到回执
    private int status;

    // 消息接收方的userId, 如果是服务器, 则是  "server"  字串
    private String to;
    // 消息发送方的userId, 如果是服务器, 则是  "server"  字串
    private String from;
    // 消息时间戳
    private long timestamp;

    /**
     * constructor
     *
     * @param messageType
     * @param from
     * @param to
     */
    public Message(int messageType, String from, String to) {
        this.messageType = messageType;
        this.messageId = UUID.randomUUID().toString();
        this.from = from;
        this.to = to;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 默认的ping消息
     * 使用者需要记得将 from 更换成客户端的 userId
     */
    private static Message pingMessage = new Message(1002, "client", "server");

    /**
     * 个默认的pong消息
     * 使用者记得将 to 更换成客户端的 userId
     */
    private static Message pongMessage = new Message(1003, "server", "client");


    /**
     * 获取默认的ping消息
     *
     * @return
     */
    public static Message obtainPingMessage() {
        return pingMessage;
    }

    /**
     * 获取默认的pong消息
     *
     * @return
     */
    public static Message obtainPongMessage() {
        return pongMessage;
    }


    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return messageType == message.messageType &&
                Objects.equals(messageId, message.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageType, messageId);
    }

    @Override
    public String toString() {
        return "Message{" +
                "messageType=" + messageType +
                ", messageId='" + messageId + '\'' +
                ", content='" + content + '\'' +
                ", status=" + status +
                ", to='" + to + '\'' +
                ", from='" + from + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}


