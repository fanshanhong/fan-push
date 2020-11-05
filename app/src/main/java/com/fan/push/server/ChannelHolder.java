package com.fan.push.server;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.Channel;

public class ChannelHolder {

    public static Map<String, Channel> channelMap = new ConcurrentHashMap<String, Channel>();

}
