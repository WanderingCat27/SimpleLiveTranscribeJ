package org.example;

import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LiveTranscriber {

    public static class tColor {
        public static final String ANSI_RESET = "\u001B[0m";
        public static final String ANSI_BLACK = "\u001B[30m";
        public static final String ANSI_RED = "\u001B[31m";
        public static final String ANSI_GREEN = "\u001B[32m";
        public static final String ANSI_YELLOW = "\u001B[33m";
        public static final String ANSI_BLUE = "\u001B[34m";
        public static final String ANSI_PURPLE = "\u001B[35m";
        public static final String ANSI_CYAN = "\u001B[36m";
        public static final String ANSI_WHITE = "\u001B[37m";

    }

    private WhisperJNI whisper = new WhisperJNI();
    private WhisperContext ctx;
    private WhisperFullParams params;
    private StringBuilder[] string_steps;
    private float[][] audioBase;
    private float[][] audioMedium;
    private int stepIndex = 0;
    private float buffer_seconds = 2f;

    private static Queue<float[]> sampleQueue = new ConcurrentLinkedQueue<>();

    public LiveTranscriber(Path modelPath, float buffer_seconds) {
        this.buffer_seconds = buffer_seconds;
        audioBase = new float[3][];
        audioMedium = new float[3][];
        string_steps = new StringBuilder[3];
        for (int i = 0; i < string_steps.length; i++) {
            string_steps[i] = new StringBuilder();
        }


        try {
            WhisperJNI.loadLibrary(); // load platform binaries
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        WhisperJNI.setLibraryLogger(null); // capture/disable whisper.cpp log
        whisper = new WhisperJNI();
        System.out.println("initing ctx");
//        ctx = whisper.init(Path.of("/Users/christiankilduff/Downloads/Distil Medium English.bin"));
        try {
            ctx = whisper.init(modelPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        params = new WhisperFullParams();
        System.out.println("starting mic");




    }

    public static void main(String[] args) throws IOException, UnsupportedAudioFileException, LineUnavailableException, InterruptedException {
        LiveTranscriber t = new LiveTranscriber(Path.of("/Users/christiankilduff/Downloads/Distil Small English Model.bin"), 2);
        MicrophoneReader mic = null;
        try {
            mic = new MicrophoneReader(t.buffer_seconds, samples -> {
                sampleQueue.add(samples);
            });
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mic.start();
        boolean hello = true;
        while (hello) {
            while (!sampleQueue.isEmpty()) {
                t.processAudio(sampleQueue.poll());
                t.printCurrentTranscript();
                writeStringToFile(t.string_steps[2].toString() + t.string_steps[1] + t.string_steps[0], "~/Downloads/t.txt");

                if(!sampleQueue.isEmpty())
                    System.err.println("BEHIND BY " + sampleQueue.size());
            }
        }

        t.close();
        mic.stop();
    }

    public String getHighConfidence() {
        return string_steps[2].toString();
    }

    public String getMediumConfidence() {
        return string_steps[1].toString();
    }
    public String getLowConfidence() {
        return string_steps[0].toString();
    }

    public void close() {
        ctx.close();
    }

    protected void printCurrentTranscript() {
        System.out.println("\n\n" + tColor.ANSI_GREEN + string_steps[2] + tColor.ANSI_CYAN + string_steps[1] + tColor.ANSI_RESET + string_steps[0]);
    }

    // does the proper transciption step for the current index
    public void processAudio(float[] samples) {
        int a = stepIndex % audioBase.length;
        /*
        s1: [3]
        s2: [2]
            0 1 2 - 3 4 5
        s1: 0 1 2 - 0 1 2
        s2: 0 - - - 2 - -
         */
        int b = stepIndex / audioBase.length;
        // save samples
        audioBase[a] = samples;
        stepIndex++;

        if (a != audioBase.length - 1) { // transcribe base if base is not full
            string_steps[0].append(transcribe(samples));
        } else {
            // concat base audio into medium length
            audioMedium[b] = concatArray(audioBase);
            string_steps[0] = new StringBuilder();

            if (b == audioMedium.length - 1) { // if filled medium buffer, transcribe high quality, skip medium transcription (would be a waste for nothing)
//                System.out.println("highQ");
                stepIndex = 0;
                string_steps[1] = new StringBuilder();
                string_steps[2].append(transcribe(concatArray(audioMedium)));
            } else { // transcribe medium quality
//                System.out.println("mediumQ");
                string_steps[1].append(transcribe(audioMedium[b]));
            }
        }


    }

    // concats a 2d array into 1
    public static float[] concatArray(float[][] arr) {
        float[] concat = new float[arr.length * arr[0].length];
        int index = 0;
        for (float[] step : arr)
            for (float f : step)
                    concat[index++] = f;
        return concat;
    }

    public String transcribe(float[] samples) {
        int result = whisper.full(ctx, params, samples, samples.length);
        if (result != 0) {
            throw new RuntimeException("Transcription failed with code " + result);
        }
        String s = "";
        try {
            int numSegments = whisper.fullNSegments(ctx);
            for(int i = 0; i < numSegments; i++)
                s+=  whisper.fullGetSegmentText(ctx, i);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("empty");

        } finally {
            return s;
        }
    }


    public static float[] readFile(Path samplePath) throws UnsupportedAudioFileException, IOException {
        // sample is a 16 bit int 16000hz little endian wav file
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(samplePath.toFile());
        // read all the available data to a little endian capture buffer
        ByteBuffer captureBuffer = ByteBuffer.allocate(audioInputStream.available());
        captureBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int read = audioInputStream.read(captureBuffer.array());
        if (read == -1) {
            throw new IOException("Empty file");
        }
        // obtain the 16 int audio samples, short type in java
        var shortBuffer = captureBuffer.asShortBuffer();
        // transform the samples to f32 samples
        float[] samples = new float[captureBuffer.capacity() / 2];
        var i = 0;
        while (shortBuffer.hasRemaining()) {
            samples[i++] = Float.max(-1f, Float.min(((float) shortBuffer.get()) / (float) Short.MAX_VALUE, 1f));
        }
        return samples;
    }
    private static void writeStringToFile(String text, String path) {
        if (path.startsWith("~")) {
            path = System.getProperty("user.home") + path.substring(1);
        }

        Path filePath = Path.of(path);
        try {
            Files.write(filePath, text.getBytes(StandardCharsets.UTF_8));
            System.out.println("File written successfully.");
        } catch (IOException e) {
            System.err.println("An error occurred while writing the file: ");
            e.printStackTrace();
        }
    }

}