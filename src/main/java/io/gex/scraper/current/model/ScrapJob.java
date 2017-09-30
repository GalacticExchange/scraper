package io.gex.scraper.current.model;


import io.gex.scraper.common.ScrapJobBase;
import org.apache.commons.lang.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class ScrapJob extends ScrapJobBase {
    private String[] urls;
    private Integer depth;
    private Duration interval;
    private Boolean extractArticle;
    private String elasticIndexName;

    public String[] getUrls() {
        return urls;
    }

    public void setUrls(String[] urls) {
        this.urls = urls;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public Boolean getExtractArticle() {
        return extractArticle;
    }

    public void setExtractArticle(Boolean extractArticle) {
        this.extractArticle = extractArticle;
    }

    public String getElasticIndexName() {
        return elasticIndexName;
    }

    public void setElasticIndexName(String elasticIndexName) {
        this.elasticIndexName = elasticIndexName;
    }

    public static Map<String, String> validate(ScrapJob conf) {
        Map<String, String> errors = new HashMap<>();
        if (conf == null) {
            errors.put("generalError", "Scrap configuration must not be null.");
            return errors;
        }

        for (String urlStr : conf.urls) {
            try {
                new URL(urlStr.trim());
            } catch (MalformedURLException e) {
                errors.put("urls", e.getMessage());
            }
        }

        if (conf.depth == null) {
            errors.put("depth", "Field must not be empty.");
        }
        if (!errors.containsKey("depth") && (conf.depth < 1 || conf.depth > 100)) {
            errors.put("depth", "Depth must must be greater than 0 and below 100.");
        }

        if (conf.interval == null) {
            errors.put("interval", "Field must not be empty.");
        }
        if (!errors.containsKey("interval")) {
            if (conf.interval.isNegative()) {
                errors.put("interval", "Interval cannot be negative.");
            } else if (conf.interval.isZero()) {
                errors.put("interval", "Interval cannot be zero.");
            }
        }

        if (conf.extractArticle == null) {
            errors.put("extractArticle", "Field must not be empty.");
        }

        if (StringUtils.isBlank(conf.elasticIndexName)) {
            errors.put("elasticIndexName", "Field must not be empty.");
        }

        return errors;
    }

}
