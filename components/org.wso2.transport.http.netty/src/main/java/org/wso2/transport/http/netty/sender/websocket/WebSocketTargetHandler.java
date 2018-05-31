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

package org.wso2.transport.http.netty.sender.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.contract.websocket.WebSocketBinaryMessage;
import org.wso2.transport.http.netty.contract.websocket.WebSocketCloseMessage;
import org.wso2.transport.http.netty.contract.websocket.WebSocketConnectorListener;
import org.wso2.transport.http.netty.contract.websocket.WebSocketControlMessage;
import org.wso2.transport.http.netty.contract.websocket.WebSocketControlSignal;
import org.wso2.transport.http.netty.contract.websocket.WebSocketFrameType;
import org.wso2.transport.http.netty.contract.websocket.WebSocketTextMessage;
import org.wso2.transport.http.netty.contractimpl.websocket.DefaultWebSocketConnection;
import org.wso2.transport.http.netty.contractimpl.websocket.DefaultWebSocketMessage;
import org.wso2.transport.http.netty.contractimpl.websocket.WebSocketInboundFrameHandler;
import org.wso2.transport.http.netty.contractimpl.websocket.message.DefaultWebSocketCloseMessage;
import org.wso2.transport.http.netty.contractimpl.websocket.message.DefaultWebSocketControlMessage;
import org.wso2.transport.http.netty.exception.UnknownWebSocketFrameTypeException;
import org.wso2.transport.http.netty.internal.websocket.WebSocketUtil;
import org.wso2.transport.http.netty.message.DefaultListener;
import org.wso2.transport.http.netty.message.HttpCarbonResponse;

import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;

/**
 * WebSocket Client Handler. This class responsible for handling the inbound messages for the WebSocket Client.
 * <i>Note: If the user uses both WebSocket Client and the server it is recommended to check the
 * <b>{@link Constants}.IS_WEBSOCKET_SERVER</b> property to identify whether the message is coming from the client
 * or the server in the application level.</i>
 */
public class WebSocketTargetHandler extends WebSocketInboundFrameHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketClient.class);

    private final WebSocketClientHandshaker handshaker;
    private final boolean isSecure;
    private final String requestedUri;
    private final WebSocketConnectorListener connectorListener;
    private String actualSubProtocol = null;
    private DefaultWebSocketConnection webSocketConnection;
    private ChannelPromise handshakeFuture;
    private ChannelHandlerContext ctx;
    private WebSocketFrameType continuationFrameType;
    private boolean closeFrameReceived;
    private CountDownLatch closeCountDownLatch = null;
    private HttpCarbonResponse httpCarbonResponse;

    public WebSocketTargetHandler(WebSocketClientHandshaker handshaker, boolean isSecure, String requestedUri,
                                  WebSocketConnectorListener webSocketConnectorListener) {
        this.handshaker = handshaker;
        this.isSecure = isSecure;
        this.requestedUri = requestedUri;
        this.connectorListener = webSocketConnectorListener;
        handshakeFuture = null;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    public void setActualSubProtocol(String actualSubProtocol) {
        this.actualSubProtocol = actualSubProtocol;
    }

    @Override
    public ChannelHandlerContext getChannelHandlerContext() {
        return this.ctx;
    }

    @Override
    public DefaultWebSocketConnection getWebSocketConnection() {
        return webSocketConnection;
    }

    public HttpCarbonResponse getHttpCarbonResponse() {
        return httpCarbonResponse;
    }

    @Override
    public boolean isCloseFrameReceived() {
        return closeFrameReceived;
    }

    @Override
    public void setCloseCountDownLatch(CountDownLatch closeCountDownLatch) {
        this.closeCountDownLatch = closeCountDownLatch;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws URISyntaxException {
        this.ctx = ctx;
        handshaker.handshake(ctx.channel());
        webSocketConnection = WebSocketUtil.getWebSocketConnection(this, isSecure, requestedUri);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (webSocketConnection != null
                && !(webSocketConnection.closeFrameReceived() || webSocketConnection.closeFrameSent())) {
            // Notify abnormal closure.
            DefaultWebSocketMessage webSocketCloseMessage =
                    new DefaultWebSocketCloseMessage(Constants.WEBSOCKET_STATUS_CODE_ABNORMAL_CLOSURE);
            setupCommonProperties(webSocketCloseMessage, ctx);
            connectorListener.onMessage((WebSocketCloseMessage) webSocketCloseMessage);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleStateEvent.ALL_IDLE_STATE_EVENT.state()) {
                notifyIdleTimeout(ctx);
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws UnknownWebSocketFrameTypeException, URISyntaxException, ServerConnectorException {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            FullHttpResponse fullHttpResponse = (FullHttpResponse) msg;
            httpCarbonResponse = setUpCarbonMessage(ctx, fullHttpResponse);
            handshaker.finishHandshake(ch, fullHttpResponse);
            log.debug("WebSocket Client connected!");
            handshakeFuture.setSuccess();
            fullHttpResponse.release();
            webSocketConnection = WebSocketUtil.getWebSocketConnection(this, isSecure, requestedUri);
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.status() +
                            ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        // If the continuation of frames are not following the protocol, netty handles them internally.
        // Hence those situations are not handled here.
        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) msg;
            if (!textFrame.isFinalFragment()) {
                continuationFrameType = WebSocketFrameType.TEXT;
            }
            notifyTextMessage(textFrame, textFrame.text(), textFrame.isFinalFragment(), ctx);
        } else if (frame instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) msg;
            if (!binaryFrame.isFinalFragment()) {
                continuationFrameType = WebSocketFrameType.BINARY;
            }
            notifyBinaryMessage(binaryFrame, binaryFrame.content(), binaryFrame.isFinalFragment(), ctx);
        } else if (frame instanceof PongWebSocketFrame) {
            notifyPongMessage((PongWebSocketFrame) frame, ctx);
        } else if (frame instanceof PingWebSocketFrame) {
            notifyPingMessage((PingWebSocketFrame) frame, ctx);
        } else if (frame instanceof CloseWebSocketFrame) {
            webSocketConnection.setCloseFrameReceived(true);
            notifyCloseMessage((CloseWebSocketFrame) frame, ctx);
        } else if (frame instanceof ContinuationWebSocketFrame) {
            ContinuationWebSocketFrame conframe = (ContinuationWebSocketFrame) msg;
            switch (continuationFrameType) {
                case TEXT:
                    notifyTextMessage(conframe, conframe.text(), conframe.isFinalFragment(), ctx);
                    break;
                case BINARY:
                    notifyBinaryMessage(conframe, conframe.content(), conframe.isFinalFragment(), ctx);
                    break;
            }
        } else {
            throw new UnknownWebSocketFrameTypeException("Cannot identify the WebSocket frame type");
        }
    }

    private void notifyTextMessage(WebSocketFrame frame, String text, boolean finalFragment,
                                   ChannelHandlerContext ctx) {
        DefaultWebSocketMessage webSocketTextMessage = WebSocketUtil.getWebSocketMessage(frame, text, finalFragment);
        setupCommonProperties(webSocketTextMessage, ctx);
        connectorListener.onMessage((WebSocketTextMessage) webSocketTextMessage);
    }

    private void notifyBinaryMessage(WebSocketFrame frame, ByteBuf content, boolean finalFragment,
                                     ChannelHandlerContext ctx) {
        DefaultWebSocketMessage webSocketBinaryMessage = WebSocketUtil.getWebSocketMessage(frame, content,
                                                                                           finalFragment);
        setupCommonProperties(webSocketBinaryMessage, ctx);
        connectorListener.onMessage((WebSocketBinaryMessage) webSocketBinaryMessage);
    }

    private void notifyCloseMessage(CloseWebSocketFrame closeWebSocketFrame, ChannelHandlerContext ctx)
            throws ServerConnectorException {
        String reasonText = closeWebSocketFrame.reasonText();
        int statusCode = closeWebSocketFrame.statusCode();
        if (webSocketConnection == null) {
            throw new ServerConnectorException("Cannot find initialized channel session");
        }
        if (closeCountDownLatch == null) {
            DefaultWebSocketMessage webSocketCloseMessage = new DefaultWebSocketCloseMessage(statusCode, reasonText);
            setupCommonProperties(webSocketCloseMessage, ctx);
            connectorListener.onMessage((WebSocketCloseMessage) webSocketCloseMessage);
            closeFrameReceived = true;
        } else {
            ctx.writeAndFlush(new CloseWebSocketFrame(statusCode, reasonText)).addListener(
                    future -> closeCountDownLatch.countDown());
        }
        closeWebSocketFrame.release();
    }

    private void notifyPingMessage(PingWebSocketFrame pingWebSocketFrame, ChannelHandlerContext ctx) {
        WebSocketControlMessage webSocketControlMessage = WebSocketUtil.
                getWebsocketControlMessage(pingWebSocketFrame, WebSocketControlSignal.PING);
        setupCommonProperties((DefaultWebSocketMessage) webSocketControlMessage, ctx);
        connectorListener.onMessage(webSocketControlMessage);
    }

    private void notifyPongMessage(PongWebSocketFrame pongWebSocketFrame, ChannelHandlerContext ctx) {
        WebSocketControlMessage webSocketControlMessage = WebSocketUtil.
                getWebsocketControlMessage(pongWebSocketFrame, WebSocketControlSignal.PONG);
        setupCommonProperties((DefaultWebSocketMessage) webSocketControlMessage, ctx);
        connectorListener.onMessage(webSocketControlMessage);
    }

    private void notifyIdleTimeout(ChannelHandlerContext ctx) {
        DefaultWebSocketMessage websocketControlMessage =
                new DefaultWebSocketControlMessage(WebSocketControlSignal.IDLE_TIMEOUT, null);
        setupCommonProperties(websocketControlMessage, ctx);
        connectorListener.onIdleTimeout((WebSocketControlMessage) websocketControlMessage);
    }

    private void setupCommonProperties(DefaultWebSocketMessage webSocketChannelContext,
                                       ChannelHandlerContext ctx) {
        webSocketChannelContext.setSubProtocol(actualSubProtocol);
        webSocketChannelContext.setIsConnectionSecured(isSecure);
        webSocketChannelContext.setWebSocketConnection(webSocketConnection);
        webSocketChannelContext.setIsServerMessage(false);

        webSocketChannelContext.setProperty(Constants.SRC_HANDLER, this);
        webSocketChannelContext.setProperty(Constants.LISTENER_PORT,
                                            ((InetSocketAddress) ctx.channel().localAddress()).getPort());
        webSocketChannelContext.setProperty(Constants.LOCAL_ADDRESS, ctx.channel().localAddress());
        webSocketChannelContext.setProperty(
                Constants.LOCAL_NAME, ((InetSocketAddress) ctx.channel().localAddress()).getHostName());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }

        //TODO: Should we close the connection without close frame?
        ctx.close();
        connectorListener.onError(cause);
    }

    private HttpCarbonResponse setUpCarbonMessage(ChannelHandlerContext ctx, HttpResponse msg) {
        HttpCarbonResponse carbonResponse = new HttpCarbonResponse(msg, new DefaultListener(ctx));
        carbonResponse.setProperty(Constants.DIRECTION, Constants.DIRECTION_RESPONSE);
        carbonResponse.setProperty(Constants.HTTP_STATUS_CODE, msg.status().code());
        return carbonResponse;
    }
}
