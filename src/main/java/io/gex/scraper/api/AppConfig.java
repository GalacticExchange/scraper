package io.gex.scraper.api;


import io.gex.scraper.archive.model.ScrapArchiveJob;
import io.gex.scraper.current.model.ScrapJob;

public class AppConfig {
    private String appId;
    private Integer webServerPort;
    private String consulHost;
    private Integer consulPort;
    private String nutchHost;
    private Integer nutchPort;
    private ScrapArchiveJob defScrapArchJob;
    private ScrapJob defScrapJob;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Integer getWebServerPort() {
        return webServerPort;
    }

    public void setWebServerPort(Integer webServerPort) {
        this.webServerPort = webServerPort;
    }

    public String getConsulHost() {
        return consulHost;
    }

    public void setConsulHost(String consulHost) {
        this.consulHost = consulHost;
    }

    public Integer getConsulPort() {
        return consulPort;
    }

    public void setConsulPort(Integer consulPort) {
        this.consulPort = consulPort;
    }

    public ScrapArchiveJob getDefScrapArchJob() {
        return defScrapArchJob;
    }

    public void setDefScrapArchJob(ScrapArchiveJob defScrapArchJob) {
        this.defScrapArchJob = defScrapArchJob;
    }

    public ScrapJob getDefScrapJob() {
        return defScrapJob;
    }

    public void setDefScrapJob(ScrapJob defScrapJob) {
        this.defScrapJob = defScrapJob;
    }

    public String getNutchHost() {
        return nutchHost;
    }

    public void setNutchHost(String nutchHost) {
        this.nutchHost = nutchHost;
    }

    public Integer getNutchPort() {
        return nutchPort;
    }

    public void setNutchPort(Integer nutchPort) {
        this.nutchPort = nutchPort;
    }
}
