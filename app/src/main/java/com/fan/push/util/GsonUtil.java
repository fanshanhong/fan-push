package com.fan.push.util;

import com.google.gson.Gson;

/**
 * Created by fan on 2016/8/11.
 */
public class GsonUtil {

    private static Gson gson = null;

    public static Gson getInstance(){
        if (gson == null){
            gson = new Gson();
        }
        return gson;
    }
}
