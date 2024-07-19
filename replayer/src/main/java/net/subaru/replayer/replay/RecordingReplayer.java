package net.subaru.replayer.replay;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import jdk.dynalink.beans.StaticClass;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.packets.AbstractSocket;
import net.runelite.client.plugins.itemstats.stats.Stat;
import net.runelite.rs.api.RSAbstractSocket;
import net.runelite.rs.api.RSBufferedNetSocket;
import net.subaru.replayer.RecordingParser;
import net.subaru.replayer.ReplayPlugin;
import net.unethicalite.api.packets.MousePackets;
import net.unethicalite.client.Static;

import javax.swing.*;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class RecordingReplayer extends Thread {
    private final List<Message> messageBuffer;
    private final Channel channel;
    private final MessageSender messageSender;
    private final TimingController timingController;
    private ReplayPlugin plugin;

    @Getter
    private volatile int messageIndex;
    @Getter
    private volatile boolean isPaused = false;
    @Getter
    private int totalTicks;
    @Getter
    private int lastPendingWrites = 0;
    public RecordingReplayer(RecordingParser recordingParser, ReplayPlugin plugin, Channel channel) {
        this.plugin = plugin;
        this.channel = channel;
        this.messageBuffer = preloadMessages(recordingParser);
        this.messageSender = new MessageSender(channel);
        this.timingController = new TimingController(messageBuffer);
        this.totalTicks = messageBuffer.size() - 2;
        lastPendingWrites = 0;
        log.info("RecordingReplayer initialized with {} messages", messageBuffer.size());
    }

    public int getCurrentTick() {
        return Math.max(0, messageIndex - 2);
    }

    private List<Message> preloadMessages(RecordingParser recordingParser) {
        return IntStream.range(0, recordingParser.getMessageCount())
                .mapToObj(i -> new Message(
                        recordingParser.readMessage(getMessageOffset(recordingParser, i), recordingParser.getMessageLength(i)),
                        recordingParser.getMessageTimestamp(i)))
                .collect(Collectors.toList());
    }

    private int getMessageOffset(RecordingParser recordingParser, int index) {
        return IntStream.range(0, index)
                .map(recordingParser::getMessageLength)
                .sum();
    }

    public boolean togglePause() {
        isPaused = !isPaused;
        if (isPaused) {
            lastPendingWrites = Static.getClient().getPacketWriter().getPendingWrites();
            timingController.pauseStarted();
        } else {
            timingController.pauseEnded();
        }
        log.info("Replay {} at message index {}", isPaused ? "paused" : "resumed", messageIndex);
        return isPaused;
    }

    public void setSpeedMultiplier(double speedMultiplier) {
        timingController.setSpeedMultiplier(speedMultiplier);
        log.info("Replay speed set to {}", speedMultiplier);
    }

    public void stepForward() {
        if (!isPaused || messageIndex >= messageBuffer.size() - 1) {
            log.info("Cannot step forward: paused={}, messageIndex={}, totalMessages={}",
                    isPaused, messageIndex, messageBuffer.size());
            return;
        }

        log.info("Stepping forward from index {}", messageIndex);
        messageSender.sendMessage(messageBuffer.get(messageIndex), messageIndex);
        messageIndex++;
        timingController.stepForward(messageBuffer.get(messageIndex).getTimestamp());
        log.info("Stepped forward to message index: {}", messageIndex);
    }

    public void goToTick(int targetTick) {
        if (targetTick < 0 || targetTick >= totalTicks) {
            log.warn("Invalid tick: {}", targetTick);
            return;
        }

        int targetIndex = targetTick + 2; // Add 2 to account for login packets
        setSpeedMultiplier(99999.0); // Set a high speed to quickly reach the target tick
        while (messageIndex < targetIndex && messageIndex < messageBuffer.size())
        {
            stepForward();
        }
        setSpeedMultiplier(1.0);
        if (!isPaused)
        {
            togglePause();
        }
    }

    public long getReplayLength() {
        if (messageBuffer.size() < 2)
        {
            return 0;
        }
        long firstMessageTime = messageBuffer.get(0).getTimestamp();
        long lastMessageTime = messageBuffer.get(messageBuffer.size() - 1).getTimestamp();
        return lastMessageTime - firstMessageTime;
    }

    public void run() {
        timingController.startReplay();

        while (messageIndex < messageBuffer.size()) {
            if (isPaused) {
                try {
                    try
                    {
                        int packets = (int) plugin.getSinceLastPacket();
                        Static.getClient().getPacketWriter().setRemainingWrites(0);
                        log.info("Packets since last: {}, Pending Writes: {}", Static.getClient().getPacketWriter().getRemainingWrites(), Static.getClient().getPacketWriter().getPendingWrites());
                    }
                    catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }

                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    log.warn("Pause interrupted", e);
                }
                continue;
            }

            Message currentMessage = messageBuffer.get(messageIndex);
            long sleepTime = timingController.calculateSleepTime(currentMessage.getTimestamp());

            log.info("Message timing: index={}, sleepTime={}", messageIndex, sleepTime);

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    log.warn("Replay interrupted", e);
                }
            }

            messageSender.sendMessage(currentMessage, messageIndex);
            timingController.messageProcessed(currentMessage.getTimestamp());
            messageIndex++;

            SwingUtilities.invokeLater(() -> {
                if (plugin.getPluginPanel() != null) {
                    plugin.getPluginPanel().updateReplayInfo();
                }
            });
        }

        log.info("Replay completed. Total messages sent: {}", messageIndex);
    }

    private static class Message {
        private final byte[] data;
        private final long timestamp;

        public Message(byte[] data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }

        public byte[] getData() {
            return data;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    private static class MessageSender {
        private final Channel channel;

        public MessageSender(Channel channel) {
            this.channel = channel;
        }

        public void sendMessage(Message message, int index) {
            ByteBuf buf = Unpooled.wrappedBuffer(message.getData());

            if (index >= 2) {
                log.info("Sending message: index={}, length={}, first few bytes: {}",
                        index, message.getData().length, bytesToHex(message.getData(), 0, Math.min(10, message.getData().length)));
                channel.writeAndFlush(buf);
                log.info("Sent message: index={}, length={}", index, message.getData().length);
            } else {
                log.info("Skipped initial message: index={}, length={}", index, message.getData().length);
            }
        }

        private static String bytesToHex(byte[] bytes, int offset, int length) {
            StringBuilder sb = new StringBuilder();
            for (int i = offset; i < offset + length; i++) {
                sb.append(String.format("%02X ", bytes[i]));
            }
            return sb.toString();
        }
    }

    private static class TimingController {
        private final List<Message> messageBuffer;
        private double speedMultiplier = 1.0;
        private long replayStartTime;
        private long lastProcessedRealTime;
        private long lastProcessedMessageTime;
        private long totalPausedTime = 0;
        private long pauseStartTime = 0;
        private boolean isPaused = false;

        public TimingController(List<Message> messageBuffer) {
            this.messageBuffer = messageBuffer;
        }

        public void startReplay() {
            replayStartTime = System.currentTimeMillis();
            lastProcessedRealTime = replayStartTime;
            lastProcessedMessageTime = messageBuffer.get(0).getTimestamp();
        }

        public void setSpeedMultiplier(double speedMultiplier) {
            this.speedMultiplier = speedMultiplier;
        }

        public void pauseStarted() {
            isPaused = true;
            pauseStartTime = System.currentTimeMillis();
        }

        public void pauseEnded() {
            isPaused = false;
            totalPausedTime += System.currentTimeMillis() - pauseStartTime;
            lastProcessedRealTime = System.currentTimeMillis();
        }

        public void stepForward(long newTimestamp) {
            long currentTime = System.currentTimeMillis();
            long timeDiff = newTimestamp - lastProcessedMessageTime;
            lastProcessedRealTime = currentTime - (long)(timeDiff / speedMultiplier);
            lastProcessedMessageTime = newTimestamp;
        }

        public void stepBackward(long newTimestamp) {
            lastProcessedMessageTime = newTimestamp;
            lastProcessedRealTime = System.currentTimeMillis();
        }

        public long calculateSleepTime(long messageTimestamp) {
            if (isPaused) {
                return 0;
            }
            long currentTime = System.currentTimeMillis();
            long elapsedMessageTime = messageTimestamp - lastProcessedMessageTime;
            long targetRealTime = lastProcessedRealTime + (long)(elapsedMessageTime / speedMultiplier);
            return Math.max(0, targetRealTime - currentTime);
        }

        public void messageProcessed(long messageTimestamp) {
            long currentTime = System.currentTimeMillis();
            lastProcessedRealTime = currentTime;
            lastProcessedMessageTime = messageTimestamp;
        }
    }
}