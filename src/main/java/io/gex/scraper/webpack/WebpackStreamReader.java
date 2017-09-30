package io.gex.scraper.webpack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class WebpackStreamReader implements Runnable {

    private BufferedReader reader;

    private static final Logger LOG = LoggerFactory.getLogger(WebpackStreamReader.class);

    public WebpackStreamReader(InputStream is) {
        this.reader = new BufferedReader(new InputStreamReader(is));
    }

    @Override
    public void run() {
        try {
            String line = reader.readLine();
            while (line != null) {
                LOG.info(line);
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
