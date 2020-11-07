package com.fan.push.client;

public class ClientMain {

    public static void main(String[] args) {
        int a = 2<<12;   //  4   8  16  32  64  128  256 512 1024 2048 4096  8192
        System.out.println(a);

        PushClient pushClient = new PushClient();
        pushClient.connect();
    }
}
