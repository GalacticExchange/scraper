package io.gex.scraper.archive.model;


public class LinkWithOffset {
    private String url;
    private Integer offset;

    public LinkWithOffset() {
    }

    public LinkWithOffset(String url, Integer offset) {
        this.url = url;
        this.offset = offset;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }
}
