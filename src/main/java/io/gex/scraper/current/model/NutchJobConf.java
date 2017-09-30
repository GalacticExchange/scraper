package io.gex.scraper.current.model;


import java.util.HashMap;
import java.util.Map;

public class NutchJobConf {
    private String crawlId;
    private String type;
    private String confId;
    private Map<String, String> args;

    public NutchJobConf() {
        args = new HashMap<>();
    }

    public String getCrawlId() {
        return crawlId;
    }

    public void setCrawlId(String crawlId) {
        this.crawlId = crawlId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getConfId() {
        return confId;
    }

    public void setConfId(String confId) {
        this.confId = confId;
    }

    public Map<String, String> getArgs() {
        return args;
    }

    public void setArgs(Map<String, String> args) {
        this.args = args;
    }
}
