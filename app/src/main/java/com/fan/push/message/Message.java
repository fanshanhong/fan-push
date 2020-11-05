package com.fan.push.message;

public class Message {

    // 消息类型   1001 握手  1002  ping  1003 pong   1004 消息
    int messageType;

    String content;

    int status;

    public Message() {
    }

    public Message(int messageType, String content, int status) {
        this.messageType = messageType;
        this.content = content;
        this.status = status;
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

    private static Message pingMessage = new Message(1002, "", 1);

    private static Message pongMessage = new Message(1003, "", 1);

    public static Message obtainPingMessage() {
        return pingMessage;
    }

    public static Message obtainPongMessage() {
        return pongMessage;
    }

}


