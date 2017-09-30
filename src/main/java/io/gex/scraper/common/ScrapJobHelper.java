package io.gex.scraper.common;


import io.gex.scraper.archive.ScrapArchiveJobExecutor;
import io.gex.scraper.archive.model.ScrapArchiveJob;
import io.gex.scraper.current.ScrapJobExecutor;
import io.gex.scraper.current.model.ScrapJob;
import org.apache.http.client.config.RequestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ScrapJobHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ScrapJobHelper.class);

    public static final int CONN_TIMEOUT = 40000;

    private static ConcurrentHashMap<String, ScrapJobBaseExecutor> activeJobs = new ConcurrentHashMap<>();

    public static void runJobsByPeriod() {
        LOG.info("Run interval runner job.");
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // don't do anything
                }

                Instant now = Instant.now();
                List<ScrapJob> jobs = ConsulHelper.getAllScrapJobs();
                jobs.stream().filter(job -> (job.getState() == ScrapJobState.ERROR || job.getState() == ScrapJobState.FINISHED)
                        && job.getStartTime().plus(job.getInterval()).isBefore(now))
                        .forEach(ScrapJobHelper::runScrapJob);
            }
        }).start();
    }

    public static void runTrackJobs() {
        LOG.info("Run tracking job.");
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // do nothing
                }

                activeJobs.entrySet().removeIf(entry -> {
                    boolean running = entry.getValue().getScrapJob().isRunning(); //need variable because of multithreading
                    try {
                        ConsulHelper.setScrapJob(entry.getValue().getScrapJob());
                    } catch (Exception e) {
                        LOG.error("Failed update job data in consul: " + e.getMessage(), e);
                        return false;
                    }
                    return !running;
                });
            }
        }).start();
    }

    public static void runScrapJob(ScrapJobBase scrapJob) {
        ScrapJobBaseExecutor executor;
        if (scrapJob instanceof ScrapJob) {
            executor = new ScrapJobExecutor((ScrapJob) scrapJob);
        } else {
            executor = new ScrapArchiveJobExecutor((ScrapArchiveJob) scrapJob);
        }
        executor.runAsync();

        ConsulHelper.setScrapJob(scrapJob);

        activeJobs.put(scrapJob.getId(), executor);
    }

    public static boolean isJobRunning(String id) {
        return activeJobs.get(id) != null;
    }

    public static ScrapJobBaseExecutor getRunningJob(String id) {
        return activeJobs.get(id);
    }

    public static void markInterruptedJobs() {
        List<ScrapJobBase> scrapJobs = new ArrayList<>();
        scrapJobs.addAll(ConsulHelper.getAllScrapArchJobs());
        scrapJobs.addAll(ConsulHelper.getAllScrapJobs());

        for (ScrapJobBase scrapJob : scrapJobs) {
            if (scrapJob.isRunning()) {
                scrapJob.setState(ScrapJobState.INTERRUPTED);
                ConsulHelper.setScrapJob(scrapJob);
            }
        }
    }

    public static RequestConfig crateDefaultRequestConf() {
        return RequestConfig.custom()
                .setSocketTimeout(CONN_TIMEOUT)
                .setConnectTimeout(CONN_TIMEOUT)
                .build();
    }
}
