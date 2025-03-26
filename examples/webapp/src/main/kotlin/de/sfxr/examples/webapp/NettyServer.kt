package de.sfxr.examples.webapp

import de.sfxr.mindi.annotations.Component
import de.sfxr.mindi.annotations.PostConstruct
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * A simple HTTP server component using Netty
 */
@Component
class NettyServer(
    private val httpHandler: HttpServerHandler,
    private val config: ServerConfig,
) : AutoCloseable {
    private val logger: Logger = LogManager.getLogger(NettyServer::class.java)

    private val bossGroup = NioEventLoopGroup(1)
    private val workerGroup = NioEventLoopGroup()

    @PostConstruct
    fun start() {
        logger.info("Starting Netty server on ${config.host}:${config.port}")

        try {
            // Create and configure the server bootstrap
            val channel =
                ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel::class.java)
                    .childHandler(
                        object : ChannelInitializer<SocketChannel>() {
                            override fun initChannel(ch: SocketChannel) {
                                ch.pipeline().apply {
                                    addLast(HttpServerCodec())
                                    addLast(HttpObjectAggregator(65536))
                                    addLast(httpHandler)
                                }
                            }
                        },
                    ).bind(config.host, config.port)
                    .sync()
                    .channel()

            logger.info("Server started successfully")
        } catch (e: Exception) {
            logger.error("Failed to start server", e)
            close()
        }
    }

    override fun close() {
        logger.info("Shutting down Netty server")
        workerGroup.shutdownGracefully()
        bossGroup.shutdownGracefully()
    }
}
