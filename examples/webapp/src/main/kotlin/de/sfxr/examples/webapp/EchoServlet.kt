package de.sfxr.examples.webapp

import de.sfxr.mindi.annotations.Component
import de.sfxr.mindi.annotations.Value
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * A simple HTTP servlet that echoes back request details
 */
@Component
class EchoServlet(
    // Example of using @Value annotation with a default
    @Value("\${echo.greeting:Hello from mindi!}")
    private val greeting: String
) {
    private val logger: Logger = LogManager.getLogger(EchoServlet::class.java)

    fun handle(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        logger.debug("Received request: ${request.uri()}")

        // Parse query parameters
        val queryStringDecoder = QueryStringDecoder(request.uri())
        val path = queryStringDecoder.path()
        val params = queryStringDecoder.parameters()

        // Simple HTML response
        val html = """
            <html>
            <head>
                <title>mindi Echo</title>
                <style>
                    body { font-family: sans-serif; margin: 40px; line-height: 1.6; }
                    h1 { color: #333366; }
                    pre { background: #f4f4f4; padding: 10px; border-radius: 5px; }
                </style>
            </head>
            <body>
                <h1>$greeting</h1>
                <p>Path: <b>$path</b></p>

                ${if (params.isNotEmpty()) {
                    """
                    <h2>Query Parameters:</h2>
                    <ul>
                        ${params.entries.joinToString("") { (key, values) ->
                            values.joinToString("") { value -> "<li><b>$key</b> = $value</li>\n" }
                        }}
                    </ul>
                    """
                } else ""}

            </body>
            </html>
        """.trimIndent()

        // Send response
        sendResponse(ctx, request, html)
    }

    private fun sendResponse(ctx: ChannelHandlerContext, request: FullHttpRequest, html: String) {
        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.copiedBuffer(html, Charsets.UTF_8)
        )

        response.headers().apply {
            set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8")
            setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())

            if (HttpUtil.isKeepAlive(request)) {
                set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                ctx.writeAndFlush(response)
            } else {
                set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
            }
        }
    }
}