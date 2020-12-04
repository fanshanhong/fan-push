package com.fan.push.client;

import com.fan.push.message.Message;

/**
 * @Description:
 * @Author: fan
 * @Date: 2020-12-03 11:06
 * @Modify:
 */
public interface INewMessageListener {
    void onNewMessageReceived(Message message);
}
