package net.subaru.replayer.record;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.subaru.replayer.ReplayPlugin;

import java.util.concurrent.ThreadFactory;

@Slf4j
public class RecordClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final ReplayPlugin replayPlugin;

    private final String address;
    private final int port;

    private final ThreadFactory threadFactory;
    private final EventLoopGroup eventLoopGroup;
    @Getter
    private RecordServerInitializer recordServerInitializer;

    private Channel serverChannel;

    public RecordClientHandler(ReplayPlugin replayPlugin, String address, int port) {
        this.replayPlugin = replayPlugin;
        this.address = address;
        this.port = port;
        this.threadFactory = new DefaultThreadFactory("server");
        this.eventLoopGroup = new NioEventLoopGroup(this.threadFactory);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Client connected: {}", ctx.channel());
        if (this.serverChannel == null) {
            this.recordServerInitializer = new RecordServerInitializer(this.replayPlugin, ctx);
            Bootstrap bootstrap = new Bootstrap()
                    .group(this.eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(this.recordServerInitializer);
            this.serverChannel = bootstrap.connect(this.address, this.port).sync().channel();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Client disconnected: {}", ctx.channel());
        if (this.serverChannel != null) {
            this.serverChannel.close().sync();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
//        log.info("{}", this.serverChannel);
//        log.info("\n{}", ByteBufUtil.prettyHexDump(msg));

        //byte[] data = new byte[msg.readableBytes()];
        //log.info("Received Client: {}", data);
        this.serverChannel.writeAndFlush(msg.retain());
    }
}
