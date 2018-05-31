/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.http.netty.contentaware;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.http.options.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.http.netty.config.ListenerConfiguration;
import org.wso2.transport.http.netty.contentaware.listeners.RequestResponseTransformStreamingListener;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.listener.ServerBootstrapConfiguration;
import org.wso2.transport.http.netty.util.TestUtil;
import org.wso2.transport.http.netty.util.server.HttpServer;
import org.wso2.transport.http.netty.util.server.initializers.EchoServerInitializer;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;

/**
 * A test case for request response transform and streaming.
 */
public class RequestResponseTransformStreamingTestCase {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseTransformStreamingTestCase.class);

    private ServerConnector serverConnector;
    private HttpServer httpServer;
    private final DefaultHttpWsConnectorFactory httpConnectorFactory = new DefaultHttpWsConnectorFactory();

    @BeforeClass
    public void setUp() {
        ListenerConfiguration listenerConfiguration = new ListenerConfiguration();
        listenerConfiguration.setPort(TestUtil.SERVER_CONNECTOR_PORT);
        serverConnector = httpConnectorFactory.createServerConnector(
                new ServerBootstrapConfiguration(new HashMap<>()), listenerConfiguration);
        ServerConnectorFuture serverConnectorFuture = serverConnector.start();
        serverConnectorFuture.setHttpConnectorListener(new RequestResponseTransformStreamingListener());
        try {
            serverConnectorFuture.sync();
        } catch (InterruptedException e) {
            log.error("Thread Interrupted while sleeping ", e);
        }
        httpServer = TestUtil.startHTTPServer(TestUtil.HTTP_SERVER_PORT, new EchoServerInitializer());
    }

    @Test
    public void testRequestResponseTransformStreaming() {
        String requestValue = "<A><B><C>Test Message</C></B></A>";
        try {
            URI baseURI = URI.create(String.format("http://%s:%d", "localhost", TestUtil.SERVER_CONNECTOR_PORT));
            HttpResponse<String> response = Unirest.post(baseURI.resolve("/").toString()).body(requestValue).asString();
            assertEquals(200, response.getStatus());
            assertEquals(requestValue, response.getBody());
        } catch (UnirestException e) {
            TestUtil.handleException(
                    "IOException occurred while running requestResponseTransformStreamingFromProcessorTestCase", e);
        }
    }

    @AfterClass
    public void cleanUp() throws ServerConnectorException {
        try {
            Unirest.shutdown();
            Options.refresh();

            List connectors = new ArrayList<>();
            connectors.add(serverConnector);
            TestUtil.cleanUp(connectors, httpServer);

            httpConnectorFactory.shutdown();
        }  catch (IOException e) {
            log.warn("IOException occurred while waiting for Unirest connection to shutdown", e);
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for HttpWsFactory to shutdown", e);
        }
    }
}
