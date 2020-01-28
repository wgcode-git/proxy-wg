package com.wg.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

import java.util.UUID;

/**
 * @description:工具类
 * @projectName:proxy-wg
 * @see:com.wg.proxy
 * @author:wanggang
 * @createTime:2020/1/28 12:39
 * @version:1.0
 */
public class Utils {
    /**
     * 生成8位短UUID
     * @return
     */
    public static String getUUID(){
        String[] chars = new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n",
                "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "0", "1", "2", "3", "4", "5", "6", "7", "8",
                "9", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
                "U", "V", "W", "X", "Y", "Z"};
        StringBuffer shortBuffer = new StringBuffer();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        for (int i = 0; i < 8; i++) {
            String str = uuid.substring(i * 4, i * 4 + 4);
            int x = Integer.parseInt(str, 16);
            shortBuffer.append(chars[x % 0x3E]);
        }
        return shortBuffer.toString();
    }

    public  static FullHttpResponse getFailResponse(){
        ByteBuf content = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("<!DOCTYPE html>\n" +
                "<html>\n" +
                "\n" +
                "<head>\n" +
                "    <meta charset='utf-8'>\n" +
                "    <meta http-equiv='X-UA-Compatible' content='IE=edge'>\n" +
                "    <title>Page Title</title>\n" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1'>\n" +
                "    <link rel='stylesheet' type='text/css' media='screen' href='main.css'>\n" +
                "    <script src='main.js'></script>\n" +
                "</head>\n" +
                "\n" +
                "<body>\n" +
                "    <div style=\"text-align: center;\">\n" +
                "        <p>BAD GETEWAY</p>\n" +
                "        <P2>the proxy can't connet to remote server</P2>\n" +
                "    </div>\n" +
                "</body>\n" +
                "\n" +
                "</html>", CharsetUtil.UTF_8));
        DefaultFullHttpResponse httpFailResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY,content);
        return httpFailResponse;
    }

    public static FullHttpResponse getOKResponse(){
        DefaultFullHttpResponse httpOKResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.valueOf(200,"Connection Established"));
        httpOKResponse.headers().set("proxy-wg","proxy-wg-1.0");
        return httpOKResponse;
    }
}
