package com.fan.push.server;

public class ServerMain {

    public static void main(String[] args) {
        PushServer pushServer = new PushServer();
        pushServer.bind();
    }
}
