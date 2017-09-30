package io.gex.scraper.common;


import java.time.Instant;

public abstract class ScrapJobBaseExecutor<T extends ScrapJobBase> {
    protected T scrapJob;
    protected Thread curThread;

    public void runAsync() {
        scrapJob.startTime = Instant.now();
        scrapJob.finishTime = null;
        scrapJob.progress.set(0);
        scrapJob.state = ScrapJobState.RUNNING;
        curThread = new Thread(this::run);
        curThread.start();
    }

    protected abstract void run();

    public abstract void stop();

    public abstract void delete() throws Exception;

    public ScrapJobBaseExecutor(T scrapJob) {
        this.scrapJob = scrapJob;
    }

    public T getScrapJob() {
        return scrapJob;
    }
}
