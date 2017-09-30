package io.gex.scraper.common;


import com.google.common.util.concurrent.AtomicDouble;

import java.time.Instant;
import java.util.UUID;

public abstract class ScrapJobBase {
    private String id;
    private String name;
    protected ScrapJobState state;
    protected AtomicDouble progress;
    protected Instant startTime;
    protected Instant finishTime;

    public void init() {
        id = UUID.randomUUID().toString();
        state = ScrapJobState.CREATED;
        progress = new AtomicDouble();
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ScrapJobState getState() {
        return state;
    }

    public void setState(ScrapJobState state) {
        this.state = state;
    }

    public Double getProgress() {
        return progress == null ? null : progress.get();
    }

    public AtomicDouble getInnerProgress() {
        return progress;
    }

    public void setProgress(AtomicDouble progress) {
        this.progress = progress;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(Instant finishTime) {
        this.finishTime = finishTime;
    }

    public boolean isRunning() {
        return ScrapJobState.RUNNING == state || ScrapJobState.STOPPING == state;
    }
}
