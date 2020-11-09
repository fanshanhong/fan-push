package com.fan.push.server;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;

public class PushServer {

    public static void main(String[] args) {
        PushServer pushServer = new PushServer();
        pushServer.bind();
    }

    void bind() {

        // bossGroup 只负责处理连接请求
        // workerGroup 负责与客户端的读写和业务处理
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();

            // 服务器端相关配置
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)// 指定 bossGroup 使用 NioServerSocketChannel 来处理连接请求
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel ch) throws Exception {

                            ch.pipeline().addLast(new IdleStateHandler(23, 0, 0, TimeUnit.SECONDS));
                            ch.pipeline().addLast(new HeartBeatServerHandler());

                            // LengthFieldPrepender 是个 MessageToMessageEncoder<ByteBuf>, 编码, 出站
                            // 输入类型是ByteBuf, 输出类型也是ByteBuf
                            ch.pipeline().addLast("lengthFieldEncoder", new LengthFieldPrepender(2));

                            // 基于帧长度的解码器, 入站
                            // 输入类型是ByteBuf, 输出类型也是ByteBuf
                            ch.pipeline().addLast("lengthFieldDecoder", new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));

                            ch.pipeline().addLast("serverHandler", new PushServerHandler());
                        }
                    });

            // 绑定端口并且同步处理
            // 这里启动了服务器
            ChannelFuture channelFuture = serverBootstrap.bind(10010).sync();


            // 对关闭通道进行监听
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
