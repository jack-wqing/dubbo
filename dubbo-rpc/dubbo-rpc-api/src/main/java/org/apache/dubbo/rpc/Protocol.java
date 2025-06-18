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
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.extension.SPI;

import java.util.Collections;
import java.util.List;

/**
 * RPC Protocol extension interface, which encapsulates the details of remote invocation. <br /><br />
 *
 * <p>Conventions:
 *
 * <li>
 *     When user invokes the 'invoke()' method in object that the method 'refer()' returns,
 *     the protocol needs to execute the 'invoke()' method of Invoker object that received by 'export()' method,
 *     which should have the same URL.
 * </li>
 *
 * <li>
 *     Invoker that returned by 'refer()' is implemented by the protocol. The remote invocation request should be sent by that Invoker.
 * </li>
 *
 * <li>
 *     The invoker that 'export()' receives will be implemented by framework. Protocol implementation should not care with that.
 * </li>
 *
 * <p>Attentions:
 *
 * <li>
 *     The Protocol implementation does not care the transparent proxy. The invoker will be converted to business interface by other layer.
 * </li>
 *
 * <li>
 *     The protocol doesn't need to be backed by TCP connection. It can also be backed by file sharing or inter-process communication.
 * </li>
 *
 * (API/SPI, Singleton, ThreadSafe)
 */
// Refer 返回的 Invoker调用invoke 将执行 receive 接受的 Invoker
// Refer Invoker 通过协议实现,  export方法接受Invoker通过Dubbo框架实现
// Protocol 不需要关注透明代理，Invoker由其他layer实现
// Protocol 不一定需要 TCP Connection

@SPI(value = "dubbo", scope = ExtensionScope.FRAMEWORK)
public interface Protocol {

    /**
     * Get default port when user doesn't config the port.
     *
     * @return default port
     */
    int getDefaultPort();

    /**
     * Export service for remote invocation: <br>
     * 1. Protocol should record request source address after receive a request:
     * RpcContext.getServerAttachment().setRemoteAddress();<br>
     * 2. export() must be idempotent, that is, there's no difference between invoking once and invoking twice when
     * export the same URL<br>
     * 3. Invoker instance is passed in by the framework, protocol needs not to care <br>
     *
     * @param <T>     Service type
     * @param invoker Service invoker
     * @return exporter reference for exported service, useful for unexport the service later
     * @throws RpcException thrown when error occurs during export the service, for example: port is occupied
     */
    // 幂等接口 RPC Context, Invoker 框架生成 服务端支持远程调用
    @Adaptive
    <T> Exporter<T> export(Invoker<T> invoker) throws RpcException;

    /**
     * Refer a remote service: <br>
     * 1. When user calls `invoke()` method of `Invoker` object which's returned from `refer()` call, the protocol
     * needs to correspondingly execute `invoke()` method of `Invoker` object <br>
     * 2. It's protocol's responsibility to implement `Invoker` which's returned from `refer()`. Generally speaking,
     * protocol sends remote request in the `Invoker` implementation. <br>
     * 3. When there's check=false set in URL, the implementation must not throw exception but try to recover when
     * connection fails.
     *
     * @param <T>  Service type
     * @param type Service class
     * @param url  URL address for the remote service
     * @return invoker service's local proxy
     * @throws RpcException when there's any error while connecting to the service provider
     */
    // 协议需要执行 Invoker的invoke方法
    // 协议需要实现 Invoker invoke方法的调用，通常是一个rpc
    @Adaptive
    <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException;

    /**
     * Destroy protocol: <br>
     * 1. Cancel all services this protocol exports and refers <br>
     * 2. Release all occupied resources, for example: connection, port, etc. <br>
     * 3. Protocol can continue to export and refer new service even after it's destroyed.
     */
    // 1. 取消协议的所有: export 和 Invoker的 服务
    // 2. 释放所有占用的资源
    // 3. destroy 之后能够继续export 和 refer服务
    void destroy();

    /**
     * Get all servers serving this protocol
     *
     * @return
     */
    default List<ProtocolServer> getServers() {
        return Collections.emptyList();
    }
}
