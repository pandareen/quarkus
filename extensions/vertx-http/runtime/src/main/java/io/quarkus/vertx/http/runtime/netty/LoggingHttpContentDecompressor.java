package io.quarkus.vertx.http.runtime.netty;

import java.nio.charset.StandardCharsets;

import org.jboss.logging.Logger;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Same behavior as Netty's {@link HttpContentDecompressor}, but when inbound body decompression fails this
 * handler logs at WARN, sends {@code 400 Bad Request}, and closes the connection (HTTP/1.x pipeline only).
 */
public final class LoggingHttpContentDecompressor extends HttpContentDecompressor {

    private static final Logger LOG = Logger.getLogger(LoggingHttpContentDecompressor.class);

    private static final byte[] ERROR_BODY = "Invalid compressed request body\r\n".getBytes(StandardCharsets.UTF_8);

    public LoggingHttpContentDecompressor(boolean strict) {
        super(strict);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOG.warnf(cause, "HTTP inbound request body decompression failed (channel id %s)",
                ctx.channel().id().asShortText());
        if (!ctx.channel().isActive()) {
            return;
        }
        if (sendDecompressionErrorResponse(ctx)) {
            return;
        }
        super.exceptionCaught(ctx, cause);
    }

    private static boolean sendDecompressionErrorResponse(ChannelHandlerContext ctx) {
        try {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.BAD_REQUEST, Unpooled.wrappedBuffer(ERROR_BODY));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            HttpUtil.setContentLength(response, response.content().readableBytes());
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            return true;
        } catch (Throwable suppressed) {
            LOG.errorf(suppressed, "Could not send HTTP 400 after inbound decompression failure");
            return false;
        }
    }
}
