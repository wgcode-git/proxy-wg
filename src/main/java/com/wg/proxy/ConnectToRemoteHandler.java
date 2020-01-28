package com.wg.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @description:
 * @projectName:proxy-wg
 * @see:com.wg.proxy
 * @author:wanggang
 * @createTime:2020/1/25 21:36
 * @version:1.0
 */
@Slf4j
@ChannelHandler.Sharable
public class ConnectToRemoteHandler extends ChannelInboundHandlerAdapter {

    private Channel channel;
    private String requestId;
    private final ByteBuf HEARTBEAT_SEQUENCE = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("HEARTBEAT", CharsetUtil.UTF_8));

    public ConnectToRemoteHandler(Channel channel, String requestId) {
        this.channel = channel;
        this.requestId = requestId;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        channel.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("{}-connectToremoteHandler exception-{}", requestId, cause.getMessage());
//        if (cause.getMessage().contains("远程主机强迫关闭了一个现有的连接")) {
            log.info("{}-connectToremoteHandler 关闭ctx", requestId);
            ctx.close();
            channel.close();
//        }else {
//            cause.printStackTrace();
//            ctx.close();
//            channel.close();
//        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        channel.write(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.info("{}-与remote服务器进行心跳检测",requestId);
            ChannelFuture remoteCf = ctx.writeAndFlush(HEARTBEAT_SEQUENCE.duplicate())
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                log.info("{}-发送心跳成功", requestId);
                            } else {
                                log.info("{}-发送心跳失败,关闭channel", requestId);
                                future.channel().close();
                                channel.close();
                            }
                        }
                    });
            log.info("{}-与client服务器进行心跳检测",requestId);
            channel.writeAndFlush(HEARTBEAT_SEQUENCE.duplicate())
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                log.info("{}-发送心跳成功",requestId);
                            } else{
                                log.info("{}-发送心跳失败,关闭channel",requestId);
                                future.channel().close();
                                remoteCf.channel().close();
                            }
                        }
                    });
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
