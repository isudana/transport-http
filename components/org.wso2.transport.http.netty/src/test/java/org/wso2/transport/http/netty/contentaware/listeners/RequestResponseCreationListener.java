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

package org.wso2.transport.http.netty.contentaware.listeners;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.config.SenderConfiguration;
import org.wso2.transport.http.netty.contract.HttpClientConnector;
import org.wso2.transport.http.netty.contract.HttpConnectorListener;
import org.wso2.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.transport.http.netty.message.HttpMessageDataStreamer;
import org.wso2.transport.http.netty.util.TestUtil;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A Message Processor which creates Request and Response
 */
public class RequestResponseCreationListener implements HttpConnectorListener {
    private Logger logger = LoggerFactory.getLogger(RequestResponseCreationListener.class);

    private String responseValue;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public RequestResponseCreationListener(String responseValue) {
        this.responseValue = responseValue;
    }

    @Override
    public void onMessage(HTTPCarbonMessage httpRequest) {
        executor.execute(() -> {
            try {
                String requestValue = TestUtil
                        .getStringFromInputStream(new HttpMessageDataStreamer(httpRequest).getInputStream());
                byte[] arry = responseValue.getBytes("UTF-8");

                HTTPCarbonMessage newMsg = httpRequest.cloneCarbonMessageWithOutData();
                if (newMsg.getHeader(HttpHeaderNames.TRANSFER_ENCODING.toString()) == null) {
                    newMsg.setHeader(HttpHeaderNames.CONTENT_LENGTH.toString(), String.valueOf(arry.length));
                }
                ByteBuffer byteBuffer1 = ByteBuffer.allocate(arry.length);
                byteBuffer1.put(arry);
                byteBuffer1.flip();
                newMsg.addHttpContent(new DefaultLastHttpContent(Unpooled.wrappedBuffer(byteBuffer1)));
                newMsg.setProperty(Constants.HTTP_HOST, TestUtil.TEST_HOST);
                newMsg.setProperty(Constants.HTTP_PORT, TestUtil.HTTP_SERVER_PORT);

                HttpWsConnectorFactory httpWsConnectorFactory = new DefaultHttpWsConnectorFactory();
                HttpClientConnector clientConnector =
                        httpWsConnectorFactory.createHttpClientConnector(new HashMap<>(), new SenderConfiguration());

                HttpResponseFuture future = clientConnector.send(newMsg);
                future.setHttpConnectorListener(new HttpConnectorListener() {
                    @Override
                    public void onMessage(HTTPCarbonMessage httpResponse) {
                        executor.execute(() -> {
                            String responseValue = TestUtil.getStringFromInputStream(
                                    new HttpMessageDataStreamer(httpResponse).getInputStream());
                            String responseStringValue = responseValue + ":" + requestValue;

                            byte[] responseByteValues = null;
                            try {
                                responseByteValues = responseStringValue.getBytes("UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                logger.error("Failed to get the byte array from responseValue", e);
                            }

                            ByteBuffer responseValueByteBuffer = ByteBuffer.wrap(responseByteValues);

                            HTTPCarbonMessage httpCarbonMessage = httpResponse
                                    .cloneCarbonMessageWithOutData();
                            if (httpCarbonMessage.getHeader(HttpHeaderNames.TRANSFER_ENCODING.toString()) == null) {
                                httpCarbonMessage.setHeader(HttpHeaderNames.CONTENT_LENGTH.toString(),
                                        String.valueOf(responseByteValues.length));
                            }
                            httpCarbonMessage.addHttpContent(
                                    new DefaultLastHttpContent(Unpooled.wrappedBuffer(responseValueByteBuffer)));

                            try {
                                httpRequest.respond(httpCarbonMessage);
                            } catch (ServerConnectorException e) {
                                logger.error("Error occurred during message notification: " + e.getMessage());
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable throwable) {

                    }
                });
            } catch (UnsupportedEncodingException e) {
                logger.error("Encoding is not supported", e);
            } catch (Exception e) {
                logger.error("Failed to send the message to the back-end", e);
            }
        });

    }

    @Override
    public void onError(Throwable throwable) {

    }
}
