package net.subaru.replayer.record;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.subaru.replayer.RecordingWriter;
import net.subaru.replayer.ReplayPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;

@Slf4j
public class RecordServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final ReplayPlugin replayPlugin;

    private final ChannelHandlerContext clientChannel;

    @Getter
    private RecordingWriter recordingWriter;

    private int[] initialIsaac;

    public RecordServerHandler(ReplayPlugin replayPlugin, ChannelHandlerContext clientChannel) {
        this.replayPlugin = replayPlugin;
        this.clientChannel = clientChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.initialIsaac = this.replayPlugin.getIsaacKey();
        log.info("Connected to the server: {}, {}", ctx.channel(), this.initialIsaac);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Calendar.getInstance().getTime());
        Path recordingPath = replayPlugin.getRecordingPath(timestamp);
        Files.createDirectories(recordingPath);
        this.recordingWriter = new RecordingWriter(recordingPath);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Disconnected from the server: {}", ctx.channel());
        this.clientChannel.close();
        if (this.recordingWriter != null) {
            this.recordingWriter.close();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        int startIndex = msg.readerIndex();

        byte[] data = new byte[msg.readableBytes()];
        //log.info("Server read: {}", ByteBufUtil.getBytes(msg));
        msg.readBytes(data);

        msg.readerIndex(startIndex);

        log.info("Server read");
        this.clientChannel.writeAndFlush(msg.retain());

        //log.info("Received Server Packet: {}", );
        //log.info("\n{}", ByteBufUtil.prettyHexDump(msg));

        if (this.recordingWriter != null) {
            if (!this.recordingWriter.isIsaacWritten()) {
                int[] isaacKey = replayPlugin.getIsaacKey();
                if (isaacKey != this.initialIsaac) {
                    log.info("Writing isaac: {}", isaacKey);
                    this.recordingWriter.writeIsaac(isaacKey);
                }
            }

            this.recordingWriter.write(data);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (this.recordingWriter != null) {
            this.recordingWriter.flush();
        }
    }
}
