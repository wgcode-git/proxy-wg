package com.wg.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @description:处理接收到的HTTP请求,并发起对远程服务器的连接
 * @projectName:proxy-wg
 * @see:com.wg.proxy
 * @author:wanggang
 * @createTime:2020/1/25 16:29
 * @version:1.0
 */
@Slf4j
@ChannelHandler.Sharable
public class HTTPServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private String host;
    private int port;
    /*client请求流水号*/
    private final String requestId;
    /*proxy与remote服务器建立连接的bootstrap*/
    private Bootstrap proxyBootstrap = new Bootstrap();
    /*proxy与remote服务器的channel*/
    private ChannelFuture cf;

    /*生成8位短UUID*/
    {
        requestId = Utils.getUUID();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("{}-HTTPServerHandler exception:{}",requestId,cause.getMessage());
//        if (cause.getMessage().contains("远程主机强迫关闭了一个现有的连接") || cause.getMessage().contains("你的主机中的软件中止了一个已建立的连接")) {
            log.info("{}-HTTPServerHandler 关闭client/remote channel", requestId);
            ctx.close();
            if (cf != null) {
                cf.channel().close();
            }
//        }else {
//            cause.printStackTrace();
//            ctx.close();
//            cf.channel().close();
//        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        log.debug("{}-读取msg",requestId);
        //判断是否为http请求
        if (msg instanceof FullHttpRequest) {
            //获取远程服务器host和port
            log.info("{}-proxy接收到的代理请求为:{}-{}", requestId, msg.method(), msg.uri());
            String uriHost = msg.headers().get("Host");
            this.host = uriHost.split(":")[0];
            this.port = 80;
            if (uriHost.split(":").length > 1) {
                this.port = Integer.valueOf(uriHost.split(":")[1]);
            }
            HttpMethod method = msg.method();
            //判断是http还是https请求
            ReferenceCountUtil.retain(msg);
            if ("CONNECT".equals(method.name())) {
                //https请求
                this.connectToRemote(ctx,msg);
                cf.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        //利用之前添加的httpCodec将okresponse写回到client,然后将其移除,保留channel进行tcp级别的透传
                        ctx.writeAndFlush(Utils.getOKResponse());
                        if (ctx.pipeline().get("httpCodec") != null) {
                            ctx.pipeline().remove("httpCodec");
                        }
                    }
                });
            } else {
                //http请求,直接发起到远程服务器的请求
                this.connectToRemote(ctx, msg);
                cf.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        //将httpCodec移除,添加一个与remote server的httpEncoder,将request发送到remote,然后将其移除,接收到返回数据后直接写回到client channel
                        if (ctx.pipeline().get("httpCodec") != null) {
                            ctx.pipeline().remove("httpCodec");
                        }
                        future.channel().pipeline().addLast("httpEncoder", new HttpRequestEncoder());
                        log.info("{}-将client发送的代理请求转向remote服务器", requestId);
                        future.channel().writeAndFlush(msg);
                        //将请求发送到远程服务器后不再需要
                        future.channel().pipeline().remove("httpEncoder");
                    }
                });
            }
        } else {
            log.info("{}-非http请求:{}", requestId, msg.toString());
        }
    }

    /**
     * 连接到远程服务器,并将ConnectToRemoteHandler添加到Pipeline中
     * 1HTTPS:将HTTP请求发送到remote,建立一条连接,并实时将remote返回的数据写回client channel中
     *2.HTTP:直接连接到远程服务器,将client连接到proxy的channel放入ConnectToRemoteHandler中,将remote返回的数据实时写入client channel中
     *      * 2.1分为两次handler,第一次建立连接的handler用来转发请求,连接建立之后的其他请求使用childHandler
     *      * 2.2.HTTPS:将HTTP请求发送到remote,建立一条连接,并实时将remote返回的数据写回client channel中
     * @param ctx client与proxy之间channel的ChannelHandlerContext变量
     * @param msg client向proxy发送的完整httprequest
     */
    private ChannelFuture connectToRemote(ChannelHandlerContext ctx, FullHttpRequest msg) throws InterruptedException {
        try {
            cf = proxyBootstrap.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .remoteAddress(host, port)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                    //处理第一次建立连接时的请求
                    .handler(new ConnectToRemoteHandler(ctx.channel(), requestId))
                    .connect()
                    //这个listener会比上面的listener先执行
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                //打开与proxy的一条tcp连接,返回200响应,在channel中透传tcp数据
                                log.info("{}-连接到远程服务器{} : {}成功", requestId, host, port);
                                //建立连接后将handler移除,便于接收后续请求
                                if (ctx.pipeline().get("aggregator") != null) {
                                    ctx.pipeline().remove("aggregator");
                                }
                                if (ctx.pipeline().get("httpServer") != null) {
                                    ctx.pipeline().remove("httpServer");
                                }
                                //如无数据传输每隔30s发起一次IdleStateEvent,与remote进行存活检测,发送心跳失败后关闭channel
                                ctx.pipeline().addLast(new IdleStateHandler(0,0,30, TimeUnit.SECONDS));
                                //处理连接建立后的后续请求
                                ctx.pipeline().addLast("remoteHandler", new ConnectToRemoteHandler(future.channel(), requestId));
                            } else {
                                log.warn("{}-连接到远程服务器{} : {}失败", requestId, host, port);
                                ctx.writeAndFlush(Utils.getFailResponse())
                                        .addListener(ChannelFutureListener.CLOSE);
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("{}-与远程服务器建立连接转发数据的过程中出错", requestId);
            e.printStackTrace();
            cf.channel().closeFuture().sync();
        } finally {
        }
        return cf;
    }
}
