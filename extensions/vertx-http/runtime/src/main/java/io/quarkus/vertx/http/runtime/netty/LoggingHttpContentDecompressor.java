package io.quarkus.vertx.http.runtime.netty;

import org.jboss.logging.Logger;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContentDecompressor;

/**
 * Same behavior as Netty's {@link HttpContentDecompressor}, but logs at WARN when inbound body
 * decompression fails before the exception propagates (HTTP/1.x pipeline only).
 */
public final class LoggingHttpContentDecompressor extends HttpContentDecompressor {

    private static final Logger LOG = Logger.getLogger(LoggingHttpContentDecompressor.class);

    public LoggingHttpContentDecompressor(boolean strict) {
        super(strict);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOG.warnf(cause, "HTTP inbound request body decompression failed (channel id %s)",
                ctx.channel().id().asShortText());
        super.exceptionCaught(ctx, cause);
    }
}
