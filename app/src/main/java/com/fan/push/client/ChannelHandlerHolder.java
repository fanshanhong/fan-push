/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fan.push.client;

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;

public class ChannelHandlerHolder {

    /**
     * 通用的业务处理Handler
     *
     * @return
     */
    public static ChannelHandler[] handlers() {
        return new ChannelHandler[]{
                new ConnectionWatchdog(),
                new IdleStateHandler(20, 5, 0, TimeUnit.SECONDS),
                new LengthFieldPrepender(2),
                new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2),
                new HeartBeatClientHandler(),
                new PushClientHandler()
        };
    }

    /**
     * 心跳Handler
     *
     * @return
     */
    public static ChannelHandler[] heartbeatHandlers() {
        // 握手成功后, 才把  watchDog 和  IdleStateHandler加入到pipeline中做心跳和重连操作.
        // 如果都没有握手成功, 就认为不是合法用户, 不需要这些

        return new ChannelHandler[]{

        };


    }
}
