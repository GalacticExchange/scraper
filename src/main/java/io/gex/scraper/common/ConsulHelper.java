package io.gex.scraper.common;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import io.gex.scraper.api.Main;
import io.gex.scraper.archive.model.ScrapArchiveJob;
import io.gex.scraper.current.model.ScrapJob;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static io.gex.scraper.api.Main.appConf;
import static io.gex.scraper.common.GsonHelper.GSON;


public class ConsulHelper {
    private static ConsulClient consulClient;

    public static void init() {
        consulClient = new ConsulClient(appConf.getConsulHost(), appConf.getConsulPort());
    }

    public static List<ScrapJob> getAllScrapJobs() {
        Response<List<GetValue>> keyValuesResponse = consulClient.getKVValues("apps/id/" + appConf.getAppId() + "/jobs/");

        List<ScrapJob> resultArray = new ArrayList<>();
        if (keyValuesResponse.getValue() != null) {
            for (GetValue value : keyValuesResponse.getValue()) {
                if (!StringUtils.endsWith(value.getKey(), "entity.json")) {
                    continue;
                }

                resultArray.add(GSON.fromJson(value.getDecodedValue(), ScrapJob.class));
            }
        }

        return resultArray;
    }


    public static ScrapJob getScrapJobById(String id) {
        Response<GetValue> response = consulClient.getKVValue("apps/id/" + appConf.getAppId() + "/jobs/" + id + "/entity.json");

        return GSON.fromJson(response.getValue().getDecodedValue(), ScrapJob.class);
    }

    public static ScrapArchiveJob getScrapArchJobById(String id) {
        Response<GetValue> response = consulClient.getKVValue("apps/id/" + appConf.getAppId() + "/archive/jobs/" + id + "/entity.json");

        return GSON.fromJson(response.getValue().getDecodedValue(), ScrapArchiveJob.class);
    }

    public static <T extends ScrapJobBase> T getScrapJobById(String id, Class<T> entityObjClass) {
        String path = "apps/id/" + appConf.getAppId() + "/" + (entityObjClass == ScrapArchiveJob.class ? "archive/" : "")
                + "jobs/" + id + "/entity.json";
        Response<GetValue> response = consulClient.getKVValue(path);

        return GSON.fromJson(response.getValue().getDecodedValue(), entityObjClass);
    }

    public static void deleteScrapJobById(String id) {
        consulClient.deleteKVValues("apps/id/" + appConf.getAppId() + "/jobs/" + id + "/");
        //todo check if request fails
    }

    public static void deleteScrapArchJobById(String id) {
        consulClient.deleteKVValues("apps/id/" + appConf.getAppId() + "/archive/jobs/" + id + "/");
        //todo check if request fails
    }

    public static List<ScrapArchiveJob> getAllScrapArchJobs() {
        Response<List<GetValue>> keyValuesResponse = consulClient.getKVValues("apps/id/" + Main.appConf.getAppId() + "/archive/jobs/");

        List<ScrapArchiveJob> resultArray = new ArrayList<>();
        if (keyValuesResponse.getValue() != null) {
            for (GetValue value : keyValuesResponse.getValue()) {
                if (!StringUtils.endsWith(value.getKey(), "entity.json")) {
                    continue;
                }

                resultArray.add(GSON.fromJson(value.getDecodedValue(), ScrapArchiveJob.class));
            }
        }

        return resultArray;
    }

    public static void setScrapJob(ScrapJobBase scrapJob) {
        String jobConfPath;
        if (scrapJob instanceof ScrapArchiveJob) {
            jobConfPath = "apps/id/" + Main.appConf.getAppId() + "/archive/jobs/" + scrapJob.getId() + "/";
        } else {
            jobConfPath = "apps/id/" + Main.appConf.getAppId() + "/jobs/" + scrapJob.getId() + "/";
        }

        consulClient.setKVValue(jobConfPath + "entity.json", GSON.toJson(scrapJob));
        //todo return boolean value
    }

}
