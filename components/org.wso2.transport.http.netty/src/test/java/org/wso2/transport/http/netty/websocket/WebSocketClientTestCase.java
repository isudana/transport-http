/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.transport.http.netty.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.contract.websocket.ClientHandshakeFuture;
import org.wso2.transport.http.netty.contract.websocket.ClientHandshakeListener;
import org.wso2.transport.http.netty.contract.websocket.WebSocketClientConnector;
import org.wso2.transport.http.netty.contract.websocket.WebSocketCloseMessage;
import org.wso2.transport.http.netty.contract.websocket.WebSocketConnection;
import org.wso2.transport.http.netty.contract.websocket.WebSocketConnectorListener;
import org.wso2.transport.http.netty.contract.websocket.WsClientConnectorConfig;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.message.HttpCarbonResponse;
import org.wso2.transport.http.netty.util.TestUtil;
import org.wso2.transport.http.netty.util.server.websocket.WebSocketRemoteServer;

import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test cases for the WebSocket Client implementation.
 */
public class WebSocketClientTestCase {

    private static final Logger log = LoggerFactory.getLogger(WebSocketClientTestCase.class);

    private DefaultHttpWsConnectorFactory httpConnectorFactory = new DefaultHttpWsConnectorFactory();
    private final String url = String.format("ws://%s:%d/%s", "localhost",
                                             TestUtil.REMOTE_WS_SERVER_PORT, "websocket");
    private static final String PING = "ping";
    private final int latchWaitTimeInSeconds = 10;
    private WsClientConnectorConfig configuration = new WsClientConnectorConfig(url);
    private WebSocketClientConnector clientConnector;
    private WebSocketRemoteServer remoteServer = new WebSocketRemoteServer(TestUtil.REMOTE_WS_SERVER_PORT,
                                                                           "xml, json");

    @BeforeClass
    public void setup() throws InterruptedException {
        remoteServer.run();
        clientConnector = httpConnectorFactory.createWsClientConnector(configuration);
    }

    @Test(description = "Test the WebSocket handshake and sending and receiving text messages.")
    public void testTextReceived() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        String textSent = "testText";
        WebSocketTestClientConnectorListener connectorListener = new WebSocketTestClientConnectorListener(latch);
        ClientHandshakeFuture handshakeFuture = handshake(connectorListener);
        handshakeFuture.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                webSocketConnection.pushText(textSent);
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.fail(t.getMessage());
            }
        });

        latch.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
        String textReceived = connectorListener.getReceivedTextToClient();
        Assert.assertEquals(textReceived, textSent);
    }

    @Test(description = "Test binary message sending and receiving.")
    public void testBinaryReceived() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        byte[] bytes = {1, 2, 3, 4, 5};
        ByteBuffer bufferSent = ByteBuffer.wrap(bytes);
        WebSocketTestClientConnectorListener connectorListener = new WebSocketTestClientConnectorListener(latch);
        ClientHandshakeFuture handshakeFuture = handshake(connectorListener);
        handshakeFuture.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                webSocketConnection.pushBinary(bufferSent);
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.fail(t.getMessage());
            }
        });

        latch.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
        ByteBuffer bufferReceived = connectorListener.getReceivedByteBufferToClient();
        Assert.assertEquals(bufferReceived, bufferSent);
    }

    @Test(description = "Test PING pong messaging.")
    public void testPingPong() throws Throwable {
        // Request PING from remote and test receive.
        CountDownLatch pingLatch = new CountDownLatch(1);
        WebSocketTestClientConnectorListener pingConnectorListener =
                new WebSocketTestClientConnectorListener(pingLatch);
        ClientHandshakeFuture pingHandshakeFuture = handshake(pingConnectorListener);
        pingHandshakeFuture.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                webSocketConnection.pushText(PING);
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.fail(t.getMessage());
            }
        });
        pingLatch.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
        Assert.assertTrue(pingConnectorListener.isPingReceived(), "Ping message should be received");

        // Test pong receive
        CountDownLatch pongLatch = new CountDownLatch(1);
        WebSocketTestClientConnectorListener pongConnectorListener =
                new WebSocketTestClientConnectorListener(pongLatch);
        ClientHandshakeFuture pongHandshakeFuture = handshake(pongConnectorListener);
        pongHandshakeFuture.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                byte[] bytes = {1, 2, 3, 4, 5};
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                webSocketConnection.ping(buffer);
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.fail(t.getMessage());
            }
        });
        pongLatch.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
        Assert.assertTrue(pongConnectorListener.isPongReceived(), "Pong message should be received");
    }

    @Test(description = "Test multiple clients handling, sending and receiving text messages for them.")
    public void testMultipleClients() throws Throwable {
        CountDownLatch latch1 = new CountDownLatch(1);
        WebSocketTestClientConnectorListener connectorListener1 = new WebSocketTestClientConnectorListener(latch1);
        String[] textsSent = {"testText1", "testText2"};
        ClientHandshakeFuture handshakeFuture1 = handshake(connectorListener1);
        handshakeFuture1.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                webSocketConnection.pushText(textsSent[0]);
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.fail(t.getMessage());
            }
        });

        latch1.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
        Assert.assertEquals(connectorListener1.getReceivedTextToClient(), textsSent[0]);

        CountDownLatch latch2 = new CountDownLatch(2);
        WebSocketTestClientConnectorListener connectorListener2 = new WebSocketTestClientConnectorListener(latch2);
        ClientHandshakeFuture handshakeFuture2 = handshake(connectorListener2);
        handshakeFuture2.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                for (String aTextsSent : textsSent) {
                    webSocketConnection.pushText(aTextsSent);
                }
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.fail(t.getMessage());
            }
        });

        latch2.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);

        for (String aTextsSent : textsSent) {
            Assert.assertEquals(connectorListener2.getReceivedTextToClient(), aTextsSent);
        }
    }

    @Test(description = "Test the idle timeout for WebSocket")
    public void testIdleTimeout() throws Throwable {
        configuration.setIdleTimeoutInMillis(1000);
        clientConnector = httpConnectorFactory.createWsClientConnector(configuration);
        CountDownLatch latch = new CountDownLatch(1);
        WebSocketTestClientConnectorListener connectorListener = new WebSocketTestClientConnectorListener(latch);
        ClientHandshakeFuture handshakeFuture = handshake(connectorListener);
        handshakeFuture.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.fail(t.getMessage());
            }
        });

        latch.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
        Assert.assertTrue(connectorListener.isIdleTimeout(), "Should reach idle timeout");
    }

    @Test(description = "Test the sub protocol negotiation with the remote server")
    public void testSubProtocolNegotiationSuccessful() throws InterruptedException {
        String[] subProtocolsSuccess = {"xmlx", "json"};
        configuration.setSubProtocols(subProtocolsSuccess);
        clientConnector = httpConnectorFactory.createWsClientConnector(configuration);
        CountDownLatch latchSuccess = new CountDownLatch(1);
        WebSocketTestClientConnectorListener connectorListenerSuccess =
                new WebSocketTestClientConnectorListener(latchSuccess);
        ClientHandshakeFuture handshakeFutureSuccess = handshake(connectorListenerSuccess);
        handshakeFutureSuccess.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                Assert.assertEquals(webSocketConnection.getSession().getNegotiatedSubprotocol(), "json");
                latchSuccess.countDown();
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.fail("Handshake failed: " + t.getMessage());
                latchSuccess.countDown();
            }
        });
        latchSuccess.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
    }

    @Test(description = "Test the sub protocol negotiation with the remote server")
    public void testSubProtocolNegotiationFail() throws InterruptedException {
        String[] subProtocolsFail = {"xmlx", "jsonx"};
        configuration.setSubProtocols(subProtocolsFail);
        clientConnector = httpConnectorFactory.createWsClientConnector(configuration);
        CountDownLatch latchFail = new CountDownLatch(1);
        WebSocketTestClientConnectorListener connectorListenerFail =
                new WebSocketTestClientConnectorListener(latchFail);
        ClientHandshakeFuture handshakeFutureFail = handshake(connectorListenerFail);
        handshakeFutureFail.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                Assert.fail("Should not negotiate");
                latchFail.countDown();
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.assertTrue(true, "Handshake failed: " + t.getMessage());
                latchFail.countDown();
            }
        });
        latchFail.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
    }

    @Test
    public void testConnectionClosureFromServerSide() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        String closeText = "close";
        WebSocketTestClientConnectorListener connectorListener = new WebSocketTestClientConnectorListener(latch);
        ClientHandshakeFuture handshakeFuture = handshake(connectorListener);
        handshakeFuture.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                webSocketConnection.pushText(closeText);
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.fail(t.getMessage());
            }
        });

        latch.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
        Assert.assertTrue(connectorListener.isClosed());
        WebSocketCloseMessage closeMessage = connectorListener.getCloseMessage();
        Assert.assertEquals(closeMessage.getCloseCode(), 1000);
        Assert.assertEquals(closeMessage.getCloseReason(), "Close on request");
    }

    @Test
    public void testConnectionClosureFromServerSideWithoutCloseFrame() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        String closeText = "close-without-frame";
        WebSocketTestClientConnectorListener connectorListener = new WebSocketTestClientConnectorListener(latch);
        ClientHandshakeFuture handshakeFuture = handshake(connectorListener);
        handshakeFuture.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                webSocketConnection.pushText(closeText);
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.fail(t.getMessage());
            }
        });

        latch.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
        WebSocketCloseMessage closeMessage = connectorListener.getCloseMessage();
        Assert.assertEquals(closeMessage.getCloseCode(), 1006);
        Assert.assertNull(closeMessage.getCloseReason());
        Assert.assertTrue(connectorListener.isClosed());
    }

    @Test(priority = 7, description = "Test the behavior of client connector when auto read is false.")
    public void autoReadFalseTest() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        String textSent = "testText";
        WebSocketTestClientConnectorListener connectorListener = new WebSocketTestClientConnectorListener(latch);
        configuration = new WsClientConnectorConfig(url);
        configuration.setAutoRead(false);
        clientConnector = httpConnectorFactory.createWsClientConnector(configuration);
        ClientHandshakeFuture handshakeFuture = handshake(connectorListener);
        AtomicReference<WebSocketConnection> wsConnection = new AtomicReference<>();
        handshakeFuture.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override
            public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                webSocketConnection.pushText(textSent);
                wsConnection.set(webSocketConnection);
            }

            @Override
            public void onError(Throwable t, HttpCarbonResponse response) {
                log.error(t.getMessage());
                Assert.fail(t.getMessage());
            }
        });

        latch.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
        String textReceived = null;
        try {
            textReceived = connectorListener.getReceivedTextToClient();
            Assert.fail("Expected exception");
        } catch (NoSuchElementException ex) {
            Assert.assertTrue(true, "Expected exception thrown");
        }
        Assert.assertNull(textReceived);
        latch = new CountDownLatch(1);
        connectorListener.setCountDownLatch(latch);
        wsConnection.get().readNextFrame();
        latch.await(latchWaitTimeInSeconds, TimeUnit.SECONDS);
        textReceived = connectorListener.getReceivedTextToClient();
        Assert.assertEquals(textReceived, textSent);
    }

    @AfterClass
    public void cleanUp() throws ServerConnectorException, InterruptedException {
        remoteServer.stop();
    }

    private ClientHandshakeFuture handshake(WebSocketConnectorListener connectorListener) {
        return clientConnector.connect(connectorListener);
    }
}
