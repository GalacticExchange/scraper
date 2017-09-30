package io.gex.scraper.webpack;

import java.io.File;
import java.io.IOException;

public class WebpackThread {
    public static void run() {
        ProcessBuilder pb = new ProcessBuilder("npm", "start");

        String projectDir = System.getProperty("user.dir");
        String staticDir = "/src/main/frontend";

        pb.directory(new File(projectDir + staticDir));

        try {
            Process p = pb.start();
            WebpackStreamReader lsr = new WebpackStreamReader(p.getInputStream());
            Thread thread = new Thread(lsr, "WebpackStreamReader");
            thread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
