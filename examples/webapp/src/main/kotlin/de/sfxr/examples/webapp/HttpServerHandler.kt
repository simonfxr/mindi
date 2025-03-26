package de.sfxr.examples.webapp

import de.sfxr.mindi.annotations.Component
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpRequest
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Simple HTTP request handler using dependency injection
 */
@Component
class HttpServerHandler(
    private val echoServlet: EchoServlet
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    private val logger: Logger = LogManager.getLogger(HttpServerHandler::class.java)

    override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        logger.debug("Processing request: ${request.uri()}")
        echoServlet.handle(ctx, request)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("Error handling request", cause)
        ctx.close()
    }
}