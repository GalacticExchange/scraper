package io.gex.scraper.archive;


import com.google.common.util.concurrent.AtomicDouble;
import io.gex.scraper.archive.model.*;
import io.gex.scraper.common.ConsulHelper;
import io.gex.scraper.common.GsonHelper;
import io.gex.scraper.common.ScrapJobBaseExecutor;
import io.gex.scraper.common.ScrapJobState;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import static io.gex.scraper.common.ScrapJobHelper.crateDefaultRequestConf;

public class ScrapArchiveJobExecutor extends ScrapJobBaseExecutor<ScrapArchiveJob> {
    private static final Logger LOG = LoggerFactory.getLogger(ScrapArchiveJobExecutor.class);

    private BlockingQueue<RespFetcherValue> httpRespsToFetch = new ArrayBlockingQueue<>(1);
    private boolean stop;
    private HttpRespsFetcher respFetcher;

    public ScrapArchiveJobExecutor(ScrapArchiveJob scrapJob) {
        super(scrapJob);
    }

    @Override
    protected void run() {
        stop = false;
        respFetcher = null;
        try {
            LOG.info("Started loading indexes list");
            List<CCIndex> indexList;
            try {
                Integer fromYear = scrapJob.getFromYear(), toYear = scrapJob.getToYear();
                indexList = getAllCCIndexes().stream().filter(indx -> (fromYear == null || indx.getCreationYear() >= fromYear)
                        && (toYear == null || indx.getCreationYear() <= toYear)).collect(Collectors.toList());
                LOG.info("Loaded indexes list");
            } catch (Exception e) {
                throw new Exception("Cannot get common crawl index from " + scrapJob.getCrawlIndexesHost() + ": " + e.getMessage(), e);
            }

            final double progressPart = 100.0 / scrapJob.getUrls().length / indexList.size();
            AtomicDouble progress = scrapJob.getInnerProgress();
            respFetcher = new HttpRespsFetcher(httpRespsToFetch, scrapJob);
            respFetcher.start();
            for (String url : scrapJob.getUrls()) {
                if (stop) {
                    break;
                }
                String host;
                try {
                    host = new URL(url).getHost();
                    LOG.info("Started processing host " + host);
                } catch (MalformedURLException e) {
                    LOG.error("Cannot parse url " + url + ": " + e.getMessage(), e);
                    progress.addAndGet(progressPart * indexList.size());
                    continue;
                }

                for (CCIndex index : indexList) {
                    if (stop) {
                        break;
                    }
                    String indexPath = index.getName() + "-index";
                    LOG.info("Started loading amount of pages in index " + index.getName() + " for URL " + url);
                    int pages;
                    try {
                        pages = getWarcLinksPages(host, indexPath).getPages();
                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                        progress.addAndGet(progressPart);
                        continue;
                    }
                    if (pages < 1) {
                        LOG.info("No pages found in index " + index.getName() + " for URL " + url);
                        progress.addAndGet(progressPart);
                        continue;
                    }
                    LOG.info("Found " + pages + " pages in index " + index.getName() + " for URL " + url);

                    for (int pageNum = 0; pageNum < pages; pageNum++) {
                        if (stop) {
                            break;
                        }
                        List<LinkToWarc> links;
                        try {
                            links = getWarcLinksForDomain(host, indexPath, pageNum);
                        } catch (Exception e) {
                            LOG.error(e.getMessage(), e);
                            continue;
                        }
                        LOG.info("Found " + links.size() + " links in page " + pageNum + " in index " + index.getName() + " for URL " + url);

                        links = links.stream().filter(link -> (link.getStatus() == null
                                || link.getStatus() >= 200 && link.getStatus() < 300)
                                && (link.getMime() == null || "text/html".equals(link.getMime()))).collect(Collectors.toList());
                        LOG.info("After filter " + links.size() + " links left in page " + pageNum + " in index "
                                + index.getName() + " for URL " + url);

                        try {
                            httpRespsToFetch.put(new RespFetcherValue(links, index.getName(), pageNum, host, progress, progressPart));
                        } catch (Exception e) {
                            LOG.error("Failed to put value to fetch queue: " + e.getMessage(), e);
                        }
                    }
                }
            }

            try {
                if (!stop) {
                    httpRespsToFetch.put(new RespFetcherValue(true));
                }
            } catch (InterruptedException e) {
                LOG.error("Failed to put value to fetcher thread: " + e.getMessage(), e);
            }
            try {
                respFetcher.join();
            } catch (InterruptedException e) {
                LOG.error("Failed to wait for finished fetcher thread: " + e.getMessage(), e);
            }

            if (!stop) {
                progress.set(100);
                scrapJob.setState(ScrapJobState.FINISHED);
            } else {
                scrapJob.setState(ScrapJobState.STOPPED);
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            if (respFetcher != null) {
                try {
                    respFetcher.join();
                } catch (InterruptedException waitEx) {
                    LOG.error("Failed to wait for finished fetcher thread: " + waitEx.getMessage(), waitEx);
                }
            }
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
        if (respFetcher != null) {
            respFetcher.stopThread();
        }
    }

    private List<CCIndex> getAllCCIndexes() throws IOException, URISyntaxException {
        URI uri = new URI(scrapJob.getCrawlIndexesHost());

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpget = new HttpGet(uri);
            httpget.setConfig(crateDefaultRequestConf());

            ResponseHandler<List<CCIndex>> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status == 200) {
                    HttpEntity entity = response.getEntity();
                    if (entity == null) {
                        return new ArrayList<>();
                    }

                    String html = EntityUtils.toString(entity);
                    Document doc = Jsoup.parse(html);
                    Elements elements = doc.select("a[href]");
                    return elements.stream().filter(e -> StringUtils.startsWith(e.attr("href"), "/CC-MAIN-"))
                            .map(e -> {
                                String indexStr = e.attr("href").substring(1);
                                String[] parts = indexStr.split("-");
                                return new CCIndex(indexStr, Integer.valueOf(parts[2]), Integer.valueOf(parts[3]));
                            }).collect(Collectors.toList());
                } else {
                    throw new ClientProtocolException("Unexpected response status: " + status);
                }
            };

            for (int i = 0; i < 3; i++) {
                try {
                    return httpclient.execute(httpget, responseHandler);
                } catch (Exception e) {
                    LOG.warn("Failed attempt to get list of available indexes: " + e.getMessage(), e);
                    if (i == 2) {
                        throw new IOException(e);
                    }
                }
            }

            throw new IOException("Failed all attempts to get list of available indexes.");
        }
    }

    private IndexPagesInfo getWarcLinksPages(String domain, String crawlIndexName) throws URISyntaxException, IOException {
        URIBuilder builder = createBaseURI(domain, crawlIndexName);
        URI uri = builder.setParameter("showNumPages", "true").build();

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpget = new HttpGet(uri);
            httpget.setConfig(crateDefaultRequestConf());

            ResponseHandler<IndexPagesInfo> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    HttpEntity entity = response.getEntity();
                    if (entity == null) {
                        throw new IOException("Entity is empty for list of pages of WARC links from index " + crawlIndexName);
                    }

                    return GsonHelper.GSON.fromJson(EntityUtils.toString(entity), IndexPagesInfo.class);
                } else {
                    throw new ClientProtocolException("Unexpected response status to get list of WARC links from index "
                            + crawlIndexName + ": " + status);
                }
            };

            for (int i = 0; i < 3; i++) {
                try {
                    return httpclient.execute(httpget, responseHandler);
                } catch (Exception e) {
                    LOG.warn("Failed attempt to get list of pages of WARC links from index " + crawlIndexName + ": " + e.getMessage(), e);
                    if (i == 2) {
                        throw new IOException("Failed to get list of pages of WARC links from index " + crawlIndexName + ": " + e.getMessage(), e);
                    }
                }
            }

            throw new IOException("Failed to get list of WARC links from index " + crawlIndexName);
        }
    }


    private URIBuilder createBaseURI(String domain, String crawlIndexName) throws URISyntaxException {
        URIBuilder builder = new URIBuilder(scrapJob.getCrawlIndexesHost()).setPath(crawlIndexName)
                .setParameter("url", domain).setParameter("output", "json")
                .setParameter("matchType", "domain");
        if (scrapJob.getCrawlLinksLimit() != null && scrapJob.getCrawlLinksLimit() > 0) {
            builder = builder.setParameter("limit", String.valueOf(scrapJob.getCrawlLinksLimit()));
        }

        return builder;
    }

    private List<LinkToWarc> getWarcLinksForDomain(String domain, String crawlIndexName, int pageNum) throws URISyntaxException, IOException {
        URIBuilder builder = createBaseURI(domain, crawlIndexName).addParameter("page", String.valueOf(pageNum));

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpget = new HttpGet(builder.build());
            httpget.setConfig(crateDefaultRequestConf());

            ResponseHandler<List<LinkToWarc>> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    HttpEntity entity = response.getEntity();
                    if (entity == null) {
                        return new ArrayList<>();
                    }

                    String[] rawJsons = EntityUtils.toString(entity).split("\n");
                    List<LinkToWarc> results = new ArrayList<>(rawJsons.length);
                    for (String rawJson : rawJsons) {
                        results.add(GsonHelper.GSON.fromJson(rawJson, LinkToWarc.class));
                    }

                    return results;
                } else {
                    throw new ClientProtocolException("Unexpected response status to get list of WARC links from index " + crawlIndexName
                            + " page " + pageNum + ": " + status);
                }
            };

            for (int i = 0; i < 3; i++) {
                try {
                    return httpclient.execute(httpget, responseHandler);
                } catch (Exception e) {
                    LOG.warn("Failed attempt to get list of WARC links from index " + crawlIndexName + ": " + e.getMessage(), e);
                    if (i == 2) {
                        throw new IOException("Failed to get list of WARC links from index " + crawlIndexName
                                + " page " + pageNum + ": " + e.getMessage(), e);
                    }
                }
            }

            throw new IOException("Failed to get list of WARC links from index " + crawlIndexName + " page " + pageNum);
        }
    }

    @Override
    public void delete() {
        if (scrapJob.isRunning()) {
            throw new IllegalStateException("Cannot delete running job.");
        }

        ConsulHelper.deleteScrapArchJobById(scrapJob.getId());
    }
}
