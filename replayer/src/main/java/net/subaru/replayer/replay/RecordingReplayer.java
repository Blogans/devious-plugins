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
    private volatile boolean isPaused = false;
    private long pauseStartTime = 0;
    private long totalPausedTime = 0;

    public RecordingReplayer(RecordingParser recordingParser, Channel channel) {
        this.recordingParser = recordingParser;
        this.channel = channel;
    }

    public boolean togglePause() {
        isPaused = !isPaused;
        if (isPaused) {
            pauseStartTime = System.currentTimeMillis();
        } else {
            totalPausedTime += System.currentTimeMillis() - pauseStartTime;
        }
        return isPaused;
    }

    @Override
    public void run() {
        int messageOffset = 0;
        long lastTimestamp = -1;
        long replayStartTime = System.currentTimeMillis();

        while (this.messageIndex < this.recordingParser.getMessageCount()) {
            while (isPaused) {
                try {
                    Thread.sleep(100); // Sleep while paused to reduce CPU usage
                } catch (InterruptedException e) {
                    log.warn("Pause interrupted", e);
                }
            }

            long timestamp = this.recordingParser.getMessageTimestamp(this.messageIndex);
            int messageLength = this.recordingParser.getMessageLength(this.messageIndex);

            if (lastTimestamp != -1) {
                long deltaTime = timestamp - lastTimestamp;
                long adjustedReplayStartTime = replayStartTime + totalPausedTime;
                long targetTime = adjustedReplayStartTime + (long) ((timestamp - recordingParser.getMessageTimestamp(0)) / speedMultiplier);
                long currentTime = System.currentTimeMillis();
                long sleepTime = targetTime - currentTime;

                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        log.warn("Replay interrupted", e);
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