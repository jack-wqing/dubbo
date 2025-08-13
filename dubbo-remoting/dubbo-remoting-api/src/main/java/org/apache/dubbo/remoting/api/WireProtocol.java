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
package org.apache.dubbo.remoting.api;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.remoting.api.pu.ChannelOperator;
import org.apache.dubbo.remoting.api.ssl.ContextOperator;

/**
 * 负责处理数据传输格式和协议细节
 *  数据序列化和反序列化 协议格式定义与解析
 *  网络传输适配
 *  消息类型处理
 */
@SPI(scope = ExtensionScope.FRAMEWORK)
public interface WireProtocol {

    ProtocolDetector detector();
    // server
    void configServerProtocolHandler(URL url, ChannelOperator operator);

    void configClientPipeline(URL url, ChannelOperator operator, ContextOperator contextOperator);

    void close();
}
