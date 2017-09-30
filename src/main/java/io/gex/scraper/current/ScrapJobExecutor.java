package io.gex.scraper.current;


import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.JsonObject;
import io.gex.scraper.api.Main;
import io.gex.scraper.common.*;
import io.gex.scraper.current.model.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;

public class ScrapJobExecutor extends ScrapJobBaseExecutor<ScrapJob> {
    private static final Logger LOG = LoggerFactory.getLogger(ScrapJobExecutor.class);

    private CloseableHttpClient httpclient;
    private URI baseUri;
    private boolean stop;

    public ScrapJobExecutor(ScrapJob scrapJob) {
        super(scrapJob);
    }

    @Override
    public void run() {
        LOG.info("Scrap job started.");
        stop = false;
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            this.httpclient = httpclient;
            baseUri = new URIBuilder(Main.appConf.getNutchHost()).setPort(Main.appConf.getNutchPort()).build();

            createNutchConfiguration(scrapJob.getId());
            LOG.info("Created nutch configuration.");

            String seedFolder = createSeedList();
            LOG.info("Created seed list.");
            final double progressPart = 100.0 / scrapJob.getDepth() / 5;
            LOG.info("Started inject job.");
            NutchJobInfo curJobInf = injectJob(scrapJob.getId(), seedFolder);
            waitForJobExecution(curJobInf.getId());
            LOG.info("Finished inject job.");
            AtomicDouble progress = scrapJob.getInnerProgress();
            for (int i = 0; i < scrapJob.getDepth() && !stop; i++) {
                LOG.info("Started iteration # " + i);

                LOG.info("Started generate job.");
                curJobInf = generateJob(scrapJob.getId());
                curJobInf = waitForJobExecution(curJobInf.getId());
                if (stop) {
                    break;
                }
                if (Integer.valueOf((String) curJobInf.getResult().get("result")) == 1) {
                    LOG.info("Generate returned 1 (no new segments created). Escaping loop: no more URLs to fetch now.");
                    progress.addAndGet(progressPart * 5);
                    continue;
                }
                progress.addAndGet(progressPart);
                LOG.info("Finished generate job.");

                LOG.info("Started fetch job.");
                curJobInf = fetchJob(scrapJob.getId());
                waitForJobExecution(curJobInf.getId());
                if (stop) {
                    break;
                }
                progress.addAndGet(progressPart);
                LOG.info("Finished fetch job.");

                LOG.info("Started parse job.");
                curJobInf = parseJob(scrapJob.getId());
                waitForJobExecution(curJobInf.getId());
                if (stop) {
                    break;
                }
                progress.addAndGet(progressPart);
                LOG.info("Finished parse job.");

                LOG.info("Started update DB job.");
                curJobInf = updateDbJob(scrapJob.getId());
                waitForJobExecution(curJobInf.getId());
                if (stop) {
                    break;
                }
                progress.addAndGet(progressPart);
                LOG.info("Finished update DB job.");

                LOG.info("Started index job.");
                curJobInf = indexJob(scrapJob.getId());
                waitForJobExecution(curJobInf.getId());
                progress.addAndGet(progressPart);
                LOG.info("Finished index job.");
                LOG.info("Finished iteration # " + i);
            }

            if (stop) {
                scrapJob.setState(ScrapJobState.STOPPED);
            } else {
                progress.set(100);
                scrapJob.setState(ScrapJobState.FINISHED);
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            scrapJob.setState(ScrapJobState.ERROR);
        } finally {
            scrapJob.setFinishTime(Instant.now());
            LOG.info("Job finished.");
        }
    }

    @Override
    public void stop() {
        stop = true;
        scrapJob.setState(ScrapJobState.STOPPING);
        if (curThread != null) {
            curThread.interrupt();
        }
    }

    private String createSeedList() throws URISyntaxException, IOException {
        URI uri = new URIBuilder(baseUri).setPath("/seed/create").build();
        JsonObject postData = new JsonObject();
        postData.addProperty("name", scrapJob.getId());
        postData.add("seedUrls", GsonHelper.GSON.toJsonTree(scrapJob.getUrls()));

        HttpPost httpPost = new HttpPost(uri);
        httpPost.setConfig(ScrapJobHelper.crateDefaultRequestConf());
        StringEntity requestEntity = new StringEntity(postData.toString(), ContentType.APPLICATION_JSON);
        httpPost.setEntity(requestEntity);

        HttpResponse rawResponse = httpclient.execute(httpPost);
        int status = rawResponse.getStatusLine().getStatusCode();
        if (status == 200) {
            return EntityUtils.toString(rawResponse.getEntity());
        } else {
            throw new IOException("Failed create seed list request. Status: " + status);
        }
    }

    private NutchJobInfo injectJob(String crawlId, String seedFolder) throws IOException, URISyntaxException {
        NutchJobConf nutchJobConf = new NutchJobConf();
        nutchJobConf.setCrawlId(crawlId);
        nutchJobConf.setConfId(getNutchConfId(crawlId));
        nutchJobConf.setType(NutchJobType.INJECT.toString());
        nutchJobConf.getArgs().put("url_dir", seedFolder);
        nutchJobConf.getArgs().put("overwrite", String.valueOf(true));
        return createJob(nutchJobConf);
    }

    private NutchJobInfo generateJob(String crawlId) throws IOException, URISyntaxException {
        NutchJobConf nutchJobConf = new NutchJobConf();
        nutchJobConf.setCrawlId(crawlId);
        nutchJobConf.setConfId(getNutchConfId(crawlId));
        nutchJobConf.setType(NutchJobType.GENERATE.toString());
        return createJob(nutchJobConf);
    }

    private NutchJobInfo fetchJob(String crawlId) throws IOException, URISyntaxException {
        NutchJobConf nutchJobConf = new NutchJobConf();
        nutchJobConf.setCrawlId(crawlId);
        nutchJobConf.setConfId(getNutchConfId(crawlId));
        nutchJobConf.setType(NutchJobType.FETCH.toString());
        return createJob(nutchJobConf);
    }

    private NutchJobInfo parseJob(String crawlId) throws IOException, URISyntaxException {
        NutchJobConf nutchJobConf = new NutchJobConf();
        nutchJobConf.setCrawlId(crawlId);
        nutchJobConf.setConfId(getNutchConfId(crawlId));
        nutchJobConf.setType(NutchJobType.PARSE.toString());
        return createJob(nutchJobConf);
    }

    private NutchJobInfo updateDbJob(String crawlId) throws IOException, URISyntaxException {
        NutchJobConf nutchJobConf = new NutchJobConf();
        nutchJobConf.setCrawlId(crawlId);
        nutchJobConf.setConfId(getNutchConfId(crawlId));
        nutchJobConf.setType(NutchJobType.UPDATEDB.toString());
        return createJob(nutchJobConf);
    }

    private NutchJobInfo indexJob(String crawlId) throws IOException, URISyntaxException {
        NutchJobConf nutchJobConf = new NutchJobConf();
        nutchJobConf.setCrawlId(crawlId);
        nutchJobConf.setConfId(getNutchConfId(crawlId));
        nutchJobConf.setType(NutchJobType.INDEX.toString());
        return createJob(nutchJobConf);
    }

    private NutchJobInfo waitForJobExecution(String jobId) throws IOException, URISyntaxException {
        boolean sentStopRequest = false;
        NutchJobInfo jobInfo;
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOG.info("Job interrupted.", e);
            }

            jobInfo = getJobInfo(jobId);
            if (jobInfo == null) {
                LOG.warn("Cannot get job info.");
                continue;
            }
            NutchJobState state = jobInfo.getState();
            if (state == null) {
                LOG.warn("Unknown job state.");
                continue;
            }

            if (state == NutchJobState.FINISHED || state == NutchJobState.FAILED || state == NutchJobState.KILLED) {
                break;
            }

            if (stop && !sentStopRequest) {
                try {
                    stopJob(jobId);
                    sentStopRequest = true;
                } catch (Exception e) {
                    LOG.error("Stop job request failed.", e);
                }
            }
        }

        return jobInfo;
    }

    private boolean stopJob(String jobId) throws URISyntaxException, IOException {
        URI uri = new URIBuilder(baseUri).setPath("/job/" + jobId + "/stop").build();

        HttpGet httpGet = new HttpGet(uri);
        HttpResponse rawResponse = httpclient.execute(httpGet);
        int status = rawResponse.getStatusLine().getStatusCode();
        if (status == 200) {
            return Boolean.valueOf(EntityUtils.toString(rawResponse.getEntity()));
        } else {
            throw new IOException("Failed stop job request. Status: " + status);
        }
    }

    private NutchJobInfo getJobInfo(String jobId) throws URISyntaxException, IOException {
        URI uri = new URIBuilder(baseUri).setPath("/job/" + jobId).build();

        HttpGet httpGet = new HttpGet(uri);
        HttpResponse rawResponse = httpclient.execute(httpGet);
        int status = rawResponse.getStatusLine().getStatusCode();
        if (status == 200) {
            return GsonHelper.GSON.fromJson(EntityUtils.toString(rawResponse.getEntity()), NutchJobInfo.class);
        } else {
            throw new IOException("Failed job info request. Status: " + status);
        }
    }

    private NutchJobInfo createJob(NutchJobConf nutchJobConf) throws URISyntaxException, IOException {
        URI uri = new URIBuilder(baseUri).setPath("/job/create").build();

        HttpPost httpPost = new HttpPost(uri);
        httpPost.setConfig(ScrapJobHelper.crateDefaultRequestConf());
        StringEntity requestEntity = new StringEntity(GsonHelper.GSON.toJson(nutchJobConf), ContentType.APPLICATION_JSON);
        httpPost.setEntity(requestEntity);

        HttpResponse rawResponse = httpclient.execute(httpPost);
        int status = rawResponse.getStatusLine().getStatusCode();
        if (status == 200) {
            return GsonHelper.GSON.fromJson(EntityUtils.toString(rawResponse.getEntity()), NutchJobInfo.class);
        } else {
            throw new IOException("Failed create job request. Status: " + status);
        }
    }

    private String createNutchConfiguration(String id) throws URISyntaxException, IOException {
        String confId = getNutchConfId(id);
        URI uri = new URIBuilder(baseUri).setPath("/config/create").build();

        JsonObject confPost = new JsonObject();
        confPost.addProperty("configId", confId);
        confPost.addProperty("force", true);
        JsonObject paramsObject = new JsonObject();
        paramsObject.addProperty("parser.html.extract.article", scrapJob.getExtractArticle());
        paramsObject.addProperty("scoring.depth.max", scrapJob.getDepth());
        paramsObject.addProperty("elastic.index", scrapJob.getElasticIndexName());
        confPost.add("params", paramsObject);

        HttpPost httpPost = new HttpPost(uri);
        httpPost.setConfig(ScrapJobHelper.crateDefaultRequestConf());
        StringEntity requestEntity = new StringEntity(confPost.toString(), ContentType.APPLICATION_JSON);
        httpPost.setEntity(requestEntity);

        HttpResponse rawResponse = httpclient.execute(httpPost);
        int status = rawResponse.getStatusLine().getStatusCode();
        if (status == 200) {
            return EntityUtils.toString(rawResponse.getEntity());
        } else {
            throw new IOException("Failed create configuration request. Status: " + status);
        }
    }

    @Override
    public void delete() throws IOException, URISyntaxException {
        if (scrapJob.isRunning()) {
            throw new IllegalStateException("Cannot delete running job.");
        }

        deleteNutchConfig();

        ConsulHelper.deleteScrapJobById(scrapJob.getId());
    }

    private void deleteNutchConfig() throws URISyntaxException, IOException {
        URI baseUri = new URIBuilder(Main.appConf.getNutchHost()).setPort(Main.appConf.getNutchPort()).build();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            URI uri = new URIBuilder(baseUri).setPath("/config/" + getNutchConfId(scrapJob.getId())).build();
            HttpDelete httpDelete = new HttpDelete(uri);
            httpDelete.setConfig(ScrapJobHelper.crateDefaultRequestConf());

            HttpResponse rawResponse = httpclient.execute(httpDelete);
            int status = rawResponse.getStatusLine().getStatusCode();
            if (status == 200 || status == 204) {
                //don't do anything
            } else {
                throw new IOException("Failed delete configuration request. Status: " + status);
            }
        }
    }

    private String getNutchConfId(String id) {
        return "conf-" + id;
    }

}
