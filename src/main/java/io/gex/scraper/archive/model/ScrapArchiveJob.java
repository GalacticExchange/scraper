package io.gex.scraper.archive.model;


import io.gex.scraper.common.ScrapJobBase;
import org.apache.commons.lang.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

public class ScrapArchiveJob extends ScrapJobBase {
    private String[] urls;
    private String crawlIndexesHost;
    private String warcFilesHost;
    private ElasticConf elastic;
    private Integer crawlLinksLimit;
    private Integer fromYear;
    private Integer toYear;
    private Integer fetchThreadsNum;

    public String[] getUrls() {
        return urls;
    }

    public void setUrls(String[] urls) {
        this.urls = urls;
    }

    public String getCrawlIndexesHost() {
        return crawlIndexesHost;
    }

    public void setCrawlIndexesHost(String crawlIndexesHost) {
        this.crawlIndexesHost = crawlIndexesHost;
    }

    public String getWarcFilesHost() {
        return warcFilesHost;
    }

    public void setWarcFilesHost(String warcFilesHost) {
        this.warcFilesHost = warcFilesHost;
    }

    public ElasticConf getElastic() {
        return elastic;
    }

    public void setElastic(ElasticConf elastic) {
        this.elastic = elastic;
    }

    public Integer getCrawlLinksLimit() {
        return crawlLinksLimit;
    }

    public void setCrawlLinksLimit(Integer crawlLinksLimit) {
        this.crawlLinksLimit = crawlLinksLimit;
    }

    public Integer getFromYear() {
        return fromYear;
    }

    public void setFromYear(Integer fromYear) {
        this.fromYear = fromYear;
    }

    public Integer getToYear() {
        return toYear;
    }

    public void setToYear(Integer toYear) {
        this.toYear = toYear;
    }

    public Integer getFetchThreadsNum() {
        return fetchThreadsNum;
    }

    public void setFetchThreadsNum(Integer fetchThreadsNum) {
        this.fetchThreadsNum = fetchThreadsNum;
    }

    public static Map<String, String> validate(ScrapArchiveJob conf) {
        Map<String, String> errors = new HashMap<>();
        if (conf == null) {
            errors.put("generalError", "Scrap archive configuration must not be null.");
            return errors;
        }

        for (String urlStr : conf.urls) {
            try {
                new URL(urlStr.trim());
            } catch (MalformedURLException e) {
                errors.put("urls", e.getMessage());
            }
        }

        if (StringUtils.isBlank(conf.crawlIndexesHost)) {
            errors.put("crawlIndexesHost", "Field must not be empty.");
        }
        if (!errors.containsKey("crawlIndexesHost")) {
            try {
                new URL(conf.crawlIndexesHost);
            } catch (MalformedURLException e) {
                errors.put("crawlIndexesHost", e.getMessage());
            }
        }

        if (StringUtils.isBlank(conf.warcFilesHost)) {
            errors.put("warcFilesHost", "Field must not be empty.");
        }
        if (!errors.containsKey("crawlIndexesHost")) {
            try {
                new URL(conf.warcFilesHost);
            } catch (MalformedURLException e) {
                errors.put("warcFilesHost", e.getMessage());
            }
        }

        if (conf.elastic == null) {
            errors.put("elastic", "Field must not be null.");
        }
        if (!errors.containsKey("elastic")) {
            for (Map.Entry<String, String> entry : ElasticConf.validate(conf.elastic).entrySet()) {
                errors.put("elastic." + entry.getKey(), entry.getValue());
            }
        }

        if (conf.fromYear != null) {
            if (conf.fromYear < 2010) {
                errors.put("fromYear", "From year must be greater than 2010.");
            } else if (conf.fromYear > Year.now().getValue()) {
                errors.put("fromYear", "From year must not be greater than current year.");
            } else if (conf.toYear != null && conf.fromYear > conf.toYear) {
                errors.put("fromYear", "From year must not be greater than to year.");
            }
        }

        if (conf.toYear != null) {
            if (conf.toYear < 2010) {
                errors.put("toYear", "To year must be greater than 2010.");
            } else if (conf.toYear > Year.now().getValue()) {
                errors.put("toYear", "To year must not be greater than current year.");
            } else if (conf.fromYear != null && conf.fromYear > conf.toYear) {
                errors.put("toYear", "To year must be greater than to year.");
            }
        }

        if (conf.fetchThreadsNum == null) {
            errors.put("fetchThreadsNum", "Field must not be null.");
        } else if (conf.fetchThreadsNum < 1) {
            errors.put("fetchThreadsNum", "Must be 1 or greater.");
        } else if (conf.fetchThreadsNum > 512) {
            errors.put("fetchThreadsNum", "Must not be greater than 512.");
        }

        return errors;
    }
}
