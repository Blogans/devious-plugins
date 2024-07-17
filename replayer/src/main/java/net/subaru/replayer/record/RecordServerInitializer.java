package net.subaru.replayer.record;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import net.subaru.replayer.ReplayPlugin;

public class RecordServerInitializer extends ChannelInitializer<SocketChannel> {
    private final ReplayPlugin replayPlugin;

    private final ChannelHandlerContext clientCtx;

    public RecordServerInitializer(ReplayPlugin replayPlugin, ChannelHandlerContext clientCtx) {
        this.replayPlugin = replayPlugin;
        this.clientCtx = clientCtx;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline pipeline = socketChannel.pipeline();
        pipeline.addLast("handler", new RecordServerHandler(this.replayPlugin, this.clientCtx));
    }
}
