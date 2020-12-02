package com.fan.push.server;

import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

/**
 * @Description: 用于维护全部与客户端连接的 Channel 的容器
 * @Author: fan
 * @Date: 2020-9-19 11:19
 * @Modify:
 */
public class ChannelHolder {

    private static ChannelHolder instance = new ChannelHolder();

    private ChannelHolder() {

    }

    public static ChannelHolder getInstance() {
        return instance;
    }


    // https://www.cnblogs.com/liangshu/p/12459657.html

    // userId(String) <==> channel(Channel) 映射关系
    private final ConcurrentHashMap<String, Channel> channelMap = new ConcurrentHashMap<>();

    /**
     * 判断一个通道是否有客户端在使用
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
     * 通过 channel 对象获取客户端的userId
     *
     * @param channel
     * @return
     */
    public String getUserIdByChannel(Channel channel) {
        AttributeKey<String> key = AttributeKey.valueOf("user");
        if (channel.hasAttr(key) || channel.attr(key).get() != null) {
            return channel.attr(key).get();
        }
        return null;
    }

    /**
     * 根据客户端的 userId 获取客户端的通道
     *
     * @param userId
     * @return
     */
    public Channel getChannelByUserId(String userId) {
        return this.channelMap.get(userId);
    }

    /**
     * 上线一个客户端, 要将这个Channel 加入到 ChannelMap 中进行管理
     *
     * @param channel
     * @param userId
     */
    public void online(Channel channel, String userId) {
        // 先判断用户是否在系统中登录?

        this.channelMap.put(userId, channel);
        AttributeKey<String> key = AttributeKey.valueOf("user");
        channel.attr(key).set(userId);
    }

    /**
     * 下线一个客户端, 将Channel 从 ChannelMap 中移除
     *
     * @param channel
     */
    public void offline(Channel channel) {

        String userId = null;

        // 先找到channel 对应的 userId
        AttributeKey<String> key = AttributeKey.valueOf("user");
        if (channel.hasAttr(key) || channel.attr(key).get() != null) {
            userId = channel.attr(key).get();
        }

        if (userId == null) {
            System.out.println("no userId with this channel:" + channel.toString());
            return;
        }

        if (channelMap.containsKey(userId)) {
            channelMap.remove(userId);
        }
        channel.attr(key).set("");
    }

    /**
     * 判断一个客户端是否在线
     *
     * @param userId
     * @return
     */
    public Boolean isOnline(String userId) {
        return this.channelMap.containsKey(userId) && this.channelMap.get(userId) != null;
    }

}
