package org.example;

import javax.sound.sampled.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.function.Consumer;

public class MicrophoneReader {
    private final AudioFormat format;
    private final TargetDataLine line;
    private final int sampleRate;
    private final int bufferSize;
    private final Consumer<float[]> callback;
    private boolean running;


    public MicrophoneReader(float seconds, Consumer<float[]> callback) throws LineUnavailableException, IOException {
        // Define the audio format
        this.format = new AudioFormat(16000, 16, 1, true, false); // 16000 Hz, 16 bit, mono, signed, little endian
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        this.line = (TargetDataLine) AudioSystem.getLine(info);
        this.line.open(format);
        this.bufferSize = (int) (seconds * format.getSampleRate() * format.getFrameSize());
        this.callback = callback;
        this.running = false;
        this.sampleRate = 16000;

    }

    public void start() {
        if (!running) {
            running = true;
            new Thread(this::captureAudio).start();
        }
    }

    public void stop() {
        running = false;
    }

    private void captureAudio() {
        line.start();

        byte[] buffer = new byte[bufferSize];
        ByteBuffer captureBuffer = ByteBuffer.wrap(buffer);
        captureBuffer.order(ByteOrder.LITTLE_ENDIAN);

        while (running) {
            int bytesRead = line.read(buffer, 0, buffer.length);
            if (bytesRead == -1) {
                break;
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            ShortBuffer shortBuffer = byteBuffer.asShortBuffer();


            // convert to float samples
            float[] samples = new float[shortBuffer.remaining()];
            int i = 0;
            while (shortBuffer.hasRemaining()) {
                samples[i++] = Math.max(-1f, Math.min(((float) shortBuffer.get()) / (float) Short.MAX_VALUE, 1f));
            }

            callback.accept(samples);
        }

        line.stop();
        line.close();
    }


}

