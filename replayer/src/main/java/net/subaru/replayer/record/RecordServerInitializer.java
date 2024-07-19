package net.subaru.replayer.record;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import lombok.Getter;
import net.subaru.replayer.ReplayPlugin;

public class RecordServerInitializer extends ChannelInitializer<SocketChannel> {
    private final ReplayPlugin replayPlugin;

    private final ChannelHandlerContext clientCtx;

    @Getter
    private RecordServerHandler recordServerHandler;

    public RecordServerInitializer(ReplayPlugin replayPlugin, ChannelHandlerContext clientCtx) {
        this.replayPlugin = replayPlugin;
        this.clientCtx = clientCtx;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline pipeline = socketChannel.pipeline();
        this.recordServerHandler = new RecordServerHandler(this.replayPlugin, this.clientCtx);
        pipeline.addLast("handler", recordServerHandler);
    }
}
