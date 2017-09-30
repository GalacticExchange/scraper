package io.gex.scraper.archive.model;


import org.apache.commons.lang.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class ElasticConf {

    private String host;
    private Integer port;
    private String clusterName;
    private String indexName;
    private String type;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public static Map<String, String> validate(ElasticConf conf) {
        Map<String, String> errors = new HashMap<>();

        if (conf == null) {
            errors.put("generalError", "Elasticsearch configuration must not be null.");
            return errors;
        }

        if (StringUtils.isEmpty(conf.getHost())) {
            errors.put("urls", "must not be empty.");
        } else {
            try {
                InetAddress.getByName(conf.getHost());
            } catch (UnknownHostException e) {
                errors.put("host", e.getMessage());
            }
        }

        if (conf.getPort() == null) {
            errors.put("port", "must not be empty.");
        } else if (conf.getPort() < 1) {
            errors.put("port", "must be greater than 1.");
        } else if (conf.getPort() > 65535) {
            errors.put("port", "must be under than 65535.");
        }

        if (StringUtils.isBlank(conf.getIndexName())) {
            errors.put("indexName", "must not be empty.");
        }

        if (StringUtils.isBlank(conf.getType())) {
            errors.put("type", "must not be empty.");
        }


        return errors;
    }
}
