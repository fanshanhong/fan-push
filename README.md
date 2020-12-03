

# fan-push


这是使用Netty框架实现的一个小型的推送系统.


## 主要适用的场景或者解决的问题

* 1,针对于在 局域网/内网 的简单推送业务.

* 2,在项目中, 我们经常会遇到这样一个场景: 用户 A 和 B 都处于详情页面, A 对页面进行了修改, 但是 B 无法知道, 需要服务器告知 B 刷新页面.
举个栗子: 我们在吃饭时候经常用的二维码点单页面, A 与 B 去吃饭点单, 当 A 点了一个菜, 就要告诉一下B, 在用户 B 的页面上好像弹出一个小小的气泡, 说: A 刚刚点了一个菜
这种业务场景是不用保活的. 因为都是在应用处于前台页面的时候进行推送. 保持长连接就好了.

我们主要针对业务2来实现.



## 主要功能


* Netty 客户端与服务端代码编写
    > 参考 PushClient 和 PushServer
* TCP拆包与粘包
    > 参考 LengthFieldPrepender 和 LengthFieldBasedFrameDecoder
* 长连接握手认证
    > 参考PushClient 中连接成功 / PushServerHandler 中对握手消息的处理 / PushClientHandler 对握手成功和握手失败的处理
* 心跳机制(ping, pong)
    > 参考 PushClientHandler 中的 ping 消息 / PushServerHandler 中的pong消息
* 客户端断线重连机制
    > 参考 PushClient 中 startTimerToReconnect / ConnectionWatchdog
* 消息重发机制
    > 参考 MessageRetryManager 和 MessageLooper
* 离线消息功能
    > 参考MessageRetryManager


