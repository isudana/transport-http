/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.http.netty.proxyserver;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.mockserver.integration.ClientAndProxy;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.http.netty.config.ListenerConfiguration;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.transport.http.netty.message.HttpCarbonRequest;
import org.wso2.transport.http.netty.util.TestUtil;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.mockserver.integration.ClientAndProxy.startClientAndProxy;
import static org.wso2.transport.http.netty.common.Constants.HTTPS_SCHEME;
import static org.wso2.transport.http.netty.common.Constants.HTTP_METHOD;


/**
 * A test for connecting to a proxy server over HTTPS.
 */
public class HttpsProxyServerTestCase {

    private ClientAndProxy proxy;

    @BeforeClass
    public void setup() throws InterruptedException {
        //Start proxy server.
        int proxyPort = 15427;
        proxy = startClientAndProxy(proxyPort);
        ProxyServerUtil.setUpClientAndServerConnectors(getListenerConfiguration(), HTTPS_SCHEME);
    }

    private ListenerConfiguration getListenerConfiguration() {
        ListenerConfiguration listenerConfiguration = ListenerConfiguration.getDefault();
        int serverPort = 8081;
        listenerConfiguration.setPort(serverPort);
        listenerConfiguration.setScheme(HTTPS_SCHEME);
        listenerConfiguration.setKeyStoreFile(TestUtil.getAbsolutePath(TestUtil.KEY_STORE_FILE_PATH));
        String password = "wso2carbon";
        listenerConfiguration.setKeyStorePass(password);
        return listenerConfiguration;
    }

    @Test (description = "Tests the scenario of a client connecting to a https server through proxy.")
    public void testHttpsProxyServer() {
            String testValue = "Test";
            ByteBuffer byteBuffer = ByteBuffer.wrap(testValue.getBytes(Charset.forName("UTF-8")));
            HTTPCarbonMessage msg = new HttpCarbonRequest(
                    new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "https://localhost:8081"));
            msg.setProperty(HTTP_METHOD, HttpMethod.POST.toString());
            msg.setHeader("Host", "localhost:8081");
            msg.addHttpContent(new DefaultLastHttpContent(Unpooled.wrappedBuffer(byteBuffer)));
            ProxyServerUtil.sendRequest(msg, testValue);
    }

    @AfterClass
    public void cleanUp() {
        ProxyServerUtil.shutDown();
        proxy.stop();
    }
}

