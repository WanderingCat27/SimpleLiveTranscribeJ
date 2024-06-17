package org.transc;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String args[]) {
        // Fetch the user's home directory
        String userHome = System.getProperty("user.home");

        // Construct the full path to the file in Downloads directory
        Path filePath = Paths.get(userHome, "Downloads", "Distil Small English Model.bin");

        LiveTranscriber live = new LiveTranscriber(filePath, 3, 3);
        try {
            new MicrophoneReader(2, (float[] samples) -> {
                live.processAudio(samples);
                live.printCurrentTranscript();
            }).start();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        while(true);
    }

}
