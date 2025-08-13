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
package org.apache.dubbo.rpc.protocol.tri.h12;

import org.apache.dubbo.remoting.api.ProtocolDetector;
import org.apache.dubbo.remoting.buffer.ByteBufferBackedChannelBuffer;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;
import org.apache.dubbo.remoting.buffer.ChannelBuffers;
import org.apache.dubbo.remoting.http12.HttpMethods;
import org.apache.dubbo.remoting.http12.HttpVersion;

import io.netty.handler.codec.http2.Http2CodecUtil;

// Triple 协议检查器: 表明Tri 协议是基于Http1 和 Http2建立的协议 同时检查Http1 和 Http2
public class TripleProtocolDetector implements ProtocolDetector {

    public static final String HTTP_VERSION = "HTTP_VERSION";
    private static final ChannelBuffer CLIENT_PREFACE_STRING = new ByteBufferBackedChannelBuffer(
            Http2CodecUtil.connectionPrefaceBuf().nioBuffer());

    @Override
    public Result detect(ChannelBuffer in) {
        // http1
        if (in.readableBytes() < 2) {
            return Result.needMoreData();
        }
        byte[] magics = new byte[7];
        in.getBytes(in.readerIndex(), magics, 0, 7);
        // http1的几中请求类型
        if (isHttp(magics)) {
            Result recognized = Result.recognized();
            recognized.setAttribute(HTTP_VERSION, HttpVersion.HTTP1.getVersion());
            return recognized;
        }
        in.resetReaderIndex();

        // http2 preface 前言: PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n
        int prefaceLen = CLIENT_PREFACE_STRING.readableBytes();
        int bytesRead = Math.min(in.readableBytes(), prefaceLen);
        if (bytesRead == 0 || !ChannelBuffers.prefixEquals(in, CLIENT_PREFACE_STRING, bytesRead)) {
            return Result.unrecognized();
        }
        if (bytesRead == prefaceLen) {
            Result recognized = Result.recognized();
            recognized.setAttribute(HTTP_VERSION, HttpVersion.HTTP2.getVersion());
            return recognized;
        }
        return Result.needMoreData();
    }
    // 检查请求头的HTTP协议方法
    private static boolean isHttp(byte[] magic) {
        for (int i = 0; i < 8; i++) {
            byte[] methodBytes = HttpMethods.HTTP_METHODS_BYTES[i];
            int end = methodBytes.length - 1;
            for (int j = 0; j <= end; j++) {
                if (magic[j] != methodBytes[j]) {
                    break;
                }
                if (j == end) {
                    return true;
                }
            }
        }
        return false;
    }
}
