package com.wg.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.extern.slf4j.Slf4j;

/**
 * @description:代理服务器启动类,依靠此类启动代理服务器程序
 * @projectName:proxy-wg
 * @see:com.wg.proxy
 * @author:wanggang
 * @createTime:2020/1/25 16:29
 * @version:1.0
 */
@Slf4j
public class HTTPServer {
    private final int port;

    public HTTPServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) throws InterruptedException {
        int port = 8080;
        if (args.length == 1) {
            try {
                port = Integer.valueOf(args[0]);
            } catch (NumberFormatException e) {
                log.error("输入的自定义启动端口:\"{}\"不合法", args[0]);
                return;
            }
        }
        log.info("在<<< {} >>>端口启动了代理服务器", port);
        new HTTPServer(port).bind();
    }

    public void bind() throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        try {
            b.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(this.port)
                    //存放待建立连接的队列,满了之后客户端无法在与代理建立连接
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .childOption(ChannelOption.SO_KEEPALIVE, false)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            //首先进行http编码
                            pipeline.addLast("httpCodec", new HttpServerCodec());
                            //最大接收的http请求为100MB
                            pipeline.addLast("aggregator", new HttpObjectAggregator(100 * 1024 * 1024));
                            //进行自定义handler处理
                            pipeline.addLast("httpServer", new HTTPServerHandler());
                        }
                    });
            ChannelFuture cf = b.bind().sync();
            cf.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully().sync();
            workGroup.shutdownGracefully().sync();
        }
    }
}
