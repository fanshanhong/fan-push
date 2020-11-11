package com.fan.push.message;

import java.util.Objects;
import java.util.UUID;

public class Message {

    // 消息类型   1001 握手  1002  ping  1003 pong   1004 消息
    int messageType;

    String content;

    int status;

    String messageId;

    String to;

    public Message() {
    }

    public Message(int messageType, String content, int status, String messageId) {
        this.messageType = messageType;
        this.content = content;
        this.status = status;
        this.messageId = messageId;
    }

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
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

    private static Message pingMessage = new Message(1002, "", 1, UUID.randomUUID().toString());

    private static Message pongMessage = new Message(1003, "", 1, UUID.randomUUID().toString());

    public static Message obtainPingMessage() {
        return pingMessage;
    }

    public static Message obtainPongMessage() {
        return pongMessage;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
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
                ", content='" + content + '\'' +
                ", status=" + status +
                ", messageId='" + messageId + '\'' +
                ", to='" + to + '\'' +
                '}';
    }
}


