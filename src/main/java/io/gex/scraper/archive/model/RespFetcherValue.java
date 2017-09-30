package io.gex.scraper.archive.model;


import com.google.common.util.concurrent.AtomicDouble;

import java.util.List;

public class RespFetcherValue {
    private boolean finish;
    private List<LinkToWarc> links;
    private String indexName;
    private int pageNum;
    private String baseUrl;
    private AtomicDouble progress;
    private double progressPart;

    public RespFetcherValue(List<LinkToWarc> links, String indexName, int pageNum, String baseUrl,
                            AtomicDouble progress, double progressPart) {
        this.links = links;
        this.indexName = indexName;
        this.pageNum = pageNum;
        this.baseUrl = baseUrl;
        this.progress = progress;
        this.progressPart = progressPart;
    }

    public RespFetcherValue(boolean finish) {
        this.finish = finish;
    }

    public boolean isFinish() {
        return finish;
    }

    public List<LinkToWarc> getLinks() {
        return links;
    }

    public String getIndexName() {
        return indexName;
    }

    public int getPageNum() {
        return pageNum;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public AtomicDouble getProgress() {
        return progress;
    }

    public double getProgressPart() {
        return progressPart;
    }

}
