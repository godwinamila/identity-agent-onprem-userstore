package org.wso2.carbon.identity.agent.onprem.userstore;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.agent.onprem.userstore.manager.common.UserStoreManager;
import org.wso2.carbon.identity.agent.onprem.userstore.manager.common.UserStoreManagerBuilder;

import java.nio.ByteBuffer;

/**
 * WebSocket Client Handler for Testing.
 */
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketClient.class);

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;

    private String textReceived = "";
    private ByteBuffer bufferReceived = null;

    public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.info("WebSocket Client disconnected!");
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            logger.info("WebSocket Client connected!");
            handshakeFuture.setSuccess();
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.status() +
                            ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            JSONObject resultObj = new JSONObject(textFrame.text());

            logger.info("WebSocket Client received text message: " + textFrame.text());
            textReceived = textFrame.text();

            if ("authenticate".equals((String) resultObj.get("requestType"))) {
                try {
                    JSONObject requestObj = resultObj.getJSONObject("requestData");
                    UserStoreManager userStoreManager = UserStoreManagerBuilder.getUserStoreManager();
                    boolean isAuthenticated = userStoreManager
                            .doAuthenticate(requestObj.getString("username"), requestObj.getString("password"));
                    logger.info("Authentication result : " + isAuthenticated);
                    ch.writeAndFlush(new TextWebSocketFrame(
                            String.format("{correlationId : '%s', responseData: '%s'}",
                                    (String) resultObj.get("correlationId"),
                                    "SUCCESS")));
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            } else if ("getclaims".equals((String) resultObj.get("requestType"))) {
                JSONObject requestObj = resultObj.getJSONObject("requestData");
                UserStoreManager userStoreManager = UserStoreManagerBuilder.getUserStoreManager();
                boolean isAuthenticated = userStoreManager
                        .doAuthenticate(requestObj.getString("username"), requestObj.getString("password"));
                logger.info("Authentication result : " + isAuthenticated);
                ch.writeAndFlush(new TextWebSocketFrame(
                        String.format("{correlationId : '%s', responseData: '%s'}",
                                (String) resultObj.get("correlationId"),
                                "SUCCESS")));
            }

        } else if (frame instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) frame;
            bufferReceived = binaryFrame.content().nioBuffer();
            logger.info("WebSocket Client received  binary message: " + bufferReceived.toString());
        } else if (frame instanceof PongWebSocketFrame) {
            logger.info("WebSocket Client received pong");
            PongWebSocketFrame pongFrame = (PongWebSocketFrame) frame;
            bufferReceived = pongFrame.content().nioBuffer();
        } else if (frame instanceof CloseWebSocketFrame) {
            logger.info("WebSocket Client received closing");
            ch.close();
        }
    }

    /**
     * @return the text received from the server.
     */
    public String getTextReceived() {
        return textReceived;
    }

    /**
     * @return the binary data received from the server.
     */
    public ByteBuffer getBufferReceived() {
        return bufferReceived;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!handshakeFuture.isDone()) {
            logger.error("Handshake failed : " + cause.getMessage(), cause);
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }
}

