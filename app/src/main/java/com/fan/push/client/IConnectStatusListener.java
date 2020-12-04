package com.fan.push.client;

/**
 * @Description: 连接状态变更回调
 * @Author: fan
 * @Date: 2020-12-03 15:31
 * @Modify:
 */
public interface IConnectStatusListener {

    // 连接成功
    void connectSuccess();

    // 连接失败
    void connectFail();

    // 正在连接中
    void connecting();
}
