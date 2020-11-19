package com.fan.push.util;

import com.google.gson.Gson;

/**
 * @Description: Gson 工具类, 用于获取一个 Gson 对象
 * @Author: fan
 * @Date: 2020-10-19 11:19
 * @Modify:
 */
public class GsonUtil {

    private static Gson gson = null;

    public static Gson getInstance() {

        if (gson == null) {
            gson = new Gson();
        }
        return gson;
    }
}
