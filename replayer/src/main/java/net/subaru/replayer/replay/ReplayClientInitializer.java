package net.subaru.replayer.replay;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;
import net.subaru.replayer.ReplayPlugin;

import java.nio.file.Path;

@Slf4j
public class ReplayClientInitializer extends ChannelInitializer<SocketChannel> {
    private final ReplayPlugin replayPlugin;
    private Path recordingPath;

    public ReplayClientInitializer(ReplayPlugin replayPlugin) {
        this.replayPlugin = replayPlugin;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        if (recordingPath == null) {
            throw new RuntimeException("No recording path set");
        }
        ChannelPipeline pipeline = socketChannel.pipeline();
        ReplayClientHandler handler = new ReplayClientHandler(this.replayPlugin, this.recordingPath);
        pipeline.addLast("handler", handler);
    }

    public void setRecordingPath(Path recordingPath) {
        this.recordingPath = recordingPath;
    }
}