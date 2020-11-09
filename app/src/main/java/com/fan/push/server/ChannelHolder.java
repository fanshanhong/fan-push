package com.fan.push.server;

import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public class ChannelHolder {

    private static ChannelHolder instance = new ChannelHolder();

    private ChannelHolder() {

    }

    public static ChannelHolder getInstance() {
        return instance;
    }


    // https://www.cnblogs.com/liangshu/p/12459657.html

    private final ConcurrentHashMap<String, Channel> channelMap = new ConcurrentHashMap<>();

    /**
     * 判断一个通道是否有用户在使用
     * 可做信息转发时判断该通道是否合法
     *
     * @param channel
     * @return
     */
    public boolean hasUser(Channel channel) {
        AttributeKey<String> key = AttributeKey.valueOf("user");
        return (channel.hasAttr(key) || channel.attr(key).get() != null);//netty移除了这个map的remove方法,这里的判断谨慎一点
    }

    /**
     * 上线一个用户
     *
     * @param channel
     * @param userId
     */
    public void online(Channel channel, String userId) {
        //先判断用户是否在系统中登录?
        //这部分代码个人实现,参考上面redis中的验证

        this.channelMap.put(userId, channel);
        AttributeKey<String> key = AttributeKey.valueOf("user");
        channel.attr(key).set(userId);
    }

    public void offline(Channel channel) {
        String userId = null;
        AttributeKey<String> key = AttributeKey.valueOf("user");
        if (channel.hasAttr(key) || channel.attr(key).get() != null) {
            userId = channel.attr(key).get();
        }

        if (userId == null) {
            throw new IllegalArgumentException("userId cannot is null");
        }
        if (channelMap.containsKey(userId)) {
            channelMap.remove(userId);
        }
        channel.attr(key).set("");
    }

    /**
     * 根据用户id获取该用户的通道
     *
     * @param userId
     * @return
     */
    public Channel getChannelByUserId(String userId) {
        return this.channelMap.get(userId);
    }

    /**
     * 判断一个用户是否在线
     *
     * @param userId
     * @return
     */
    public Boolean isOnline(String userId) {
        return this.channelMap.containsKey(userId) && this.channelMap.get(userId) != null;
    }

}
