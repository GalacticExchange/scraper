package io.gex.scraper.api;

import com.google.gson.JsonSyntaxException;
import io.gex.scraper.common.ConsulHelper;
import io.gex.scraper.common.GsonHelper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.gex.scraper.common.ScrapJobHelper.*;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static AppConfig appConf;

    public static void main(String[] args) {
        LOG.info("Started loading configuration file");
        try {
            appConf = loadConfig(args);
            LOG.info("Configuration file was loaded");
        } catch (IOException e) {
            LOG.error("Cannot load configuration file: " + e.getMessage(), e);
            System.exit(-1);
            return;
        }

        if (args.length > 1) {
            System.setProperty("devEnv", String.valueOf("-dev".equalsIgnoreCase(args[1])));
        }

        try {
            ConsulHelper.init();
            markInterruptedJobs();
            runJobsByPeriod();
            runTrackJobs();
            WebServer.start();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            System.exit(-1);
        }
    }

    private static AppConfig loadConfig(String[] args) throws IOException {
        if (args == null || args.length < 1 || StringUtils.isEmpty(args[0])) {
            throw new IOException("Path to configuration file not provided.");
        }

        String pathToConfStr = args[0];
        File pathToConf = new File(pathToConfStr);

        String fileContent = FileUtils.readFileToString(pathToConf, StandardCharsets.UTF_8);
        try {
            return GsonHelper.GSON.fromJson(fileContent, AppConfig.class);
        } catch (JsonSyntaxException e) {
            throw new IOException(e);
        }
    }


}
