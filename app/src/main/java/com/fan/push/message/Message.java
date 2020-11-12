package com.fan.push.message;

import java.util.Objects;
import java.util.UUID;

/**
 * 消息封装
 * <p>
 * <p>
 * 按照流程顺序, 梳理一下, 我们一共需要这几种类型的消息:
 *
 *
 * 1, 最开始客户端连接后, 客户端需要给服务端发送一个握手消息(1001) 所需字段:  messageType=1001 from=客户端userId timestamp
 * 2, 服务端收到客户端的握手消息后, 如果验证成功, 返回给客户端  1001 status=1  from=server to=客户端userId timestamp
 *                            如果验证失败, 返回给客户端  1001 status=-1 from=server to=客户端userId timestamp
 * 3, 客户端端收到 握手消息(1001) status=-1, 就知道自己握手都没成功, 直接关闭连接
 *              握手消息(1001) status=1,  就知道自己握手成功, 开始后续的逻辑:心跳
 *
 * 4, 客户端发送的心跳消息(ping),   messageType=1002, from=客户端userId, to=server, timestamp
 * 5, 服务端接收到心跳消息(ping)后, 返回响应消息(pong),  messageType=1003, from=server, to=客户端userId, timestamp
 *
 * 6, 服务端给客户端推送消息(1004), messageType=1004, messageId, content, from=server, to=客户端userId, timestamp
 * 7, 如果客户端收到服务器的推送消息, 发送已读回执. messageType=1004, messageId, from=客户端userId, to=server, status=1, timestamp
 *
 *
 */
public class Message {

    // 消息类型   1001 握手  1002 ping  1003 pong   1004 消息
    private int messageType;
    private String messageId;
    private String content;

    // 消息状态.
    // 主要用于  1001握手响应  和  1004消息的已读回执
    private int status;

    // 消息接收方的userId
    private String to;
    // 消息发送方的userId
    private String from;
    // 时间戳
    private long timestamp;

    public Message(int messageType, String messageId, String to, String from) {
        this.messageType = messageType;
        this.messageId = messageId;
        this.to = to;
        this.from = from;
        this.timestamp = System.currentTimeMillis();
    }

    private static Message pingMessage = new Message(1002, UUID.randomUUID().toString(), "client", "server");

    private static Message pongMessage = new Message(1003, UUID.randomUUID().toString(), "server", "client");


    public static Message obtainPingMessage() {
        return pingMessage;
    }

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


