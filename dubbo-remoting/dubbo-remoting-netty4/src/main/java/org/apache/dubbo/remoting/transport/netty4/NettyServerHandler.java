/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting.transport.netty4;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;

import javax.net.ssl.SSLSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;

import static org.apache.dubbo.common.constants.LoggerCodeConstants.TRANSPORT_UNEXPECTED_EXCEPTION;

/**
 * NettyServerHandler.
 */
// Dubbo 实现的Netty ChannelHandler支持 Inbound和Outbound
@io.netty.channel.ChannelHandler.Sharable
public class NettyServerHandler extends ChannelDuplexHandler {
    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(NettyServerHandler.class);
    /**
     * the cache for alive worker channel.
     * <ip:port, dubbo channel>
     */
    // ip:ort -> Channel
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    private static final AttributeKey<SSLSession> SSL_SESSION_KEY = AttributeKey.valueOf(Constants.SSL_SESSION_KEY);

    private final URL url;

    private final ChannelHandler handler;

    public NettyServerHandler(URL url, ChannelHandler handler) {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        this.url = url;
        this.handler = handler;
    }

    public Map<String, Channel> getChannels() {
        return channels;
    }
    // Netty channelActive 事件
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        io.netty.channel.Channel ch = ctx.channel();
        NettyChannel channel = NettyChannel.getOrAddChannel(ch, url, handler);
        if (channel != null) {
            channels.put(NetUtils.toAddressString(channel.getRemoteAddress()), channel);
        }
        // 映射Dubbo Channel connected
        handler.connected(channel);

        if (logger.isInfoEnabled() && channel != null) {
            logger.info(
                    "The connection {} of {} -> {} is established.",
                    ch,
                    channel.getRemoteAddressKey(),
                    channel.getLocalAddressKey());
        }
    }
    // channel Inactive事件
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        io.netty.channel.Channel ch = ctx.channel();
        NettyChannel channel = NettyChannel.getOrAddChannel(ch, url, handler);
        try {
            channels.remove(NetUtils.toAddressString(channel.getRemoteAddress()));
            // Dubbo Channel disconnected
            handler.disconnected(channel);
        } finally {
            NettyChannel.removeChannel(ch);
        }

        if (logger.isInfoEnabled()) {
            logger.info(
                    "The connection {} of {} -> {} is disconnected.",
                    ch,
                    channel.getRemoteAddressKey(),
                    channel.getLocalAddressKey());
        }
    }
    // Channel读取事件
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        // 映射Dubbo Channel received
        handler.received(channel, msg);
        // trigger qos handler
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.write(ctx, msg, promise);
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        // 发送/写消息 之后映射到Dubbo channel的Send
        handler.sent(channel, msg);
    }
    // 用户事件
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // server will close channel when server don't receive any heartbeat from client util timeout.
        // 关闭闲置连接
        if (evt instanceof IdleStateEvent) {
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
            try {
                logger.info("IdleStateEvent triggered, close channel " + channel);
                channel.close();
            } finally {
                NettyChannel.removeChannelIfDisconnected(ctx.channel());
            }
        }
        super.userEventTriggered(ctx, evt);
        // ssl握手完成事件 1个RTT  存储SSL Session消息
        if (evt instanceof SslHandshakeCompletionEvent) {
            SslHandshakeCompletionEvent handshakeEvent = (SslHandshakeCompletionEvent) evt;
            if (handshakeEvent.isSuccess()) {
                NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
                channel.setAttribute(
                        Constants.SSL_SESSION_KEY,
                        ctx.channel().attr(SSL_SESSION_KEY).get());
            }
        }
    }
    // 异常时间: 关闭连接
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        io.netty.channel.Channel ch = ctx.channel();
        NettyChannel channel = NettyChannel.getOrAddChannel(ch, url, handler);
        try {
            handler.caught(channel, cause);
        } finally {
            NettyChannel.removeChannelIfDisconnected(ch);
        }

        if (logger.isWarnEnabled()) {
            logger.warn(
                    TRANSPORT_UNEXPECTED_EXCEPTION,
                    "",
                    "",
                    channel == null
                            ? String.format("The connection %s has exception.", ch)
                            : String.format(
                                    "The connection %s of %s -> %s has exception.",
                                    ch, channel.getRemoteAddressKey(), channel.getLocalAddressKey()),
                    cause);
        }
    }
}
