package io.gex.scraper.current.model;

import java.util.Map;

public class NutchJobInfo {
    private String id;
    private NutchJobType type;
    private String confId;
    private Map<String, Object> args;
    private Map<String, Object> result;
    private NutchJobState state;
    private String msg;
    private String crawlId;

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public NutchJobState getState() {
        return state;
    }

    public void setState(NutchJobState state) {
        this.state = state;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public String getConfId() {
        return confId;
    }

    public void setConfId(String confId) {
        this.confId = confId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCrawlId() {
        return crawlId;
    }

    public void setCrawlId(String crawlId) {
        this.crawlId = crawlId;
    }

    public NutchJobType getType() {
        return type;
    }

    public void setType(NutchJobType type) {
        this.type = type;
    }
}
