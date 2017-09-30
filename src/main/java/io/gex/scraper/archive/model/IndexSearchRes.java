package io.gex.scraper.archive.model;


import java.util.List;

public class IndexSearchRes {
    private CCIndex ccIndex;
    private List<LinkToWarc> warc;

    public IndexSearchRes(CCIndex ccIndex, List<LinkToWarc> warc) {
        this.ccIndex = ccIndex;
        this.warc = warc;
    }

    public CCIndex getCcIndex() {
        return ccIndex;
    }

    public void setCcIndex(CCIndex ccIndex) {
        this.ccIndex = ccIndex;
    }

    public List<LinkToWarc> getWarc() {
        return warc;
    }

    public void setWarc(List<LinkToWarc> warc) {
        this.warc = warc;
    }
}
