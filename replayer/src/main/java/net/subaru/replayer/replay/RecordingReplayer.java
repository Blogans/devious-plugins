package net.subaru.replayer.replay;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import net.subaru.replayer.RecordingParser;

@Slf4j
public class RecordingReplayer extends Thread {
    private final RecordingParser recordingParser;

    private final Channel channel;

    private int messageIndex;
    private double speedMultiplier = 1.0; // Default speed

    public RecordingReplayer(RecordingParser recordingParser, Channel channel) {
        this.recordingParser = recordingParser;
        this.channel = channel;
    }


    @Override
    public void run() {
        int messageOffset = 0;
        long lastTimestamp = -1;
        long replayStartTime = System.currentTimeMillis();
        long virtualTime = 0;

        while (this.messageIndex < this.recordingParser.getMessageCount()) {
            long timestamp = this.recordingParser.getMessageTimestamp(this.messageIndex);
            int messageLength = this.recordingParser.getMessageLength(this.messageIndex);

            if (lastTimestamp != -1) {
                long deltaTime = timestamp - lastTimestamp;
                virtualTime += deltaTime;

                long realTimePassed = System.currentTimeMillis() - replayStartTime;
                long sleepTime = (long) (virtualTime / speedMultiplier) - realTimePassed;

                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        // Handle interruption
                    }
                }
            }

            byte[] data = this.recordingParser.readMessage(messageOffset, messageLength);
            ByteBuf buf = Unpooled.wrappedBuffer(data);

            if (messageIndex >= 2) {
                this.channel.writeAndFlush(buf);
            }

            lastTimestamp = timestamp;
            messageOffset += messageLength;
            this.messageIndex++;
        }
    }
}
