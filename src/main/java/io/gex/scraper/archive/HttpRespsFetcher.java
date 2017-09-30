package io.gex.scraper.archive;

import io.gex.scraper.archive.model.*;
import io.gex.scraper.common.GsonHelper;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.conn.DefaultHttpResponseParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.util.EntityUtils;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.warc.WARCReaderFactory;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.gex.scraper.common.ScrapJobHelper.CONN_TIMEOUT;


public class HttpRespsFetcher extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(HttpRespsFetcher.class);

    private BlockingQueue<RespFetcherValue> httpRespsToFetch;
    private ScrapArchiveJob scrapJob;
    private boolean stop;

    public HttpRespsFetcher(BlockingQueue<RespFetcherValue> httpRespsToFetch, ScrapArchiveJob scrapJob) {
        this.httpRespsToFetch = httpRespsToFetch;
        this.scrapJob = scrapJob;
    }

    @Override
    public void run() {
        LOG.info("Started fetching HTTP responses job.");
        stop = false;
        ForkJoinPool forkJoinPool = new ForkJoinPool(scrapJob.getFetchThreadsNum());
        ElasticConf elasticConf = scrapJob.getElastic();

        try (TransportClient transportClient = createElasticClient(elasticConf.getHost(),
                elasticConf.getPort(), elasticConf.getClusterName());
             BulkProcessor bulkProcessor = createBulkProc(transportClient)) {
            String previousIndex = null, previousUrl = null;
            while (!stop) {
                RespFetcherValue fetchValue;
                try {
                    fetchValue = httpRespsToFetch.take();
                    if (fetchValue.isFinish()) {
                        break;
                    }
                } catch (InterruptedException e) {
                    LOG.error("Fetching HTTP responses job interrupted. " + e.getMessage(), e);
                    break;
                }

                try {
                    LOG.info("Started loading " + fetchValue.getLinks().size() + " HTTP responses from WARC archives page "
                            + fetchValue.getPageNum() + " in index " + fetchValue.getIndexName() + " for URL " + fetchValue.getBaseUrl());
                    AtomicInteger countSentToElastic = new AtomicInteger();
                    List<LinkToWarc> links = filterIfExistsInElastic(transportClient, elasticConf.getIndexName(), fetchValue.getLinks());
                    LOG.info("After deduplication " + links.size() + " links left in page " + fetchValue.getPageNum()
                            + " in index " + fetchValue.getIndexName() + " for URL " + fetchValue.getBaseUrl());

                    if (stop) {
                        break;
                    }
                    forkJoinPool.submit(() -> links.parallelStream().map(link -> {
                        try {
                            HttpResponse response = getHttpResponseFromWarcFile(
                                    new URL(scrapJob.getWarcFilesHost() + link.getFilename()),
                                    link.getOffset(), link.getLength());

                            return getScrapData(link, response);
                        } catch (Exception e) {
                            LOG.error("Cannot get http response from archive for " + link.getUrl() + "in index "
                                    + fetchValue.getIndexName() + ": " + e.getMessage(), e);
                        }
                        return null;
                    }).filter(Objects::nonNull).forEach(data -> {
                        bulkProcessor.add(new IndexRequest(elasticConf.getIndexName(), elasticConf.getType(),
                                (String) data.get("url")).source(data));
                        countSentToElastic.incrementAndGet();
                    })).get();

                    bulkProcessor.flush();
                    LOG.info("Loaded " + countSentToElastic.get() + " HTTP responses from WARC archives in page "
                            + fetchValue.getPageNum() + " in index " + fetchValue.getIndexName() + " for URL " + fetchValue.getBaseUrl());
                } catch (Exception e) {
                    LOG.error("Failed to process page " + fetchValue.getPageNum() + " in index "
                            + fetchValue.getIndexName() + ": " + e.getMessage(), e);
                } finally {
                    if (previousIndex != null && previousUrl != null && (!previousIndex.equals(fetchValue.getIndexName())
                            || !previousUrl.equals(fetchValue.getBaseUrl()))) {
                        fetchValue.getProgress().addAndGet(fetchValue.getProgressPart());
                    }
                    previousIndex = fetchValue.getIndexName();
                    previousUrl = fetchValue.getBaseUrl();
                }
            }
        } catch (Exception e) {
            LOG.error("Error in fetching HTTP responses job: " + e.getMessage(), e);
        }

        LOG.info("Finished fetching HTTP responses job.");
    }

    private TransportClient createElasticClient(String host, int port, String clusterName) throws UnknownHostException {
        Settings.Builder settingsBuilder = Settings.settingsBuilder();
        if (StringUtils.isNotBlank(clusterName)) {
            settingsBuilder.put("cluster.name", clusterName);
        }

        return TransportClient.builder().settings(settingsBuilder.build()).build()
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
    }

    private BulkProcessor createBulkProc(TransportClient transportClient) {
        return BulkProcessor.builder(transportClient, new BulkProcessor.Listener() {
            @Override
            public void beforeBulk(long executionId, BulkRequest request) {
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {

            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
                LOG.error("Failed send data to Elasticsearch " + ": " + failure.getMessage(), failure);
            }
        }).setBulkSize(new ByteSizeValue(10, ByteSizeUnit.MB)).build();
    }

    private HttpResponse getHttpResponseFromWarcFile(URL warcUrl, long offset, long size) throws IOException, HttpException {
        for (int i = 0; i < 3; i++) {
            try {
                URLConnection urlConnection = warcUrl.openConnection();
                long end = offset + size - 1;
                urlConnection.setRequestProperty("range", "bytes=" + offset + "-" + end);
                urlConnection.setConnectTimeout(CONN_TIMEOUT);
                urlConnection.setReadTimeout(CONN_TIMEOUT);
                urlConnection.connect();
                try (InputStream inputStream = urlConnection.getInputStream()) {
                    ArchiveReader ar = WARCReaderFactory.get(warcUrl.toString(), inputStream, false);
                    ArchiveRecord archiveRecord = ar.get();
                    byte[] rawData = IOUtils.toByteArray(archiveRecord, archiveRecord.available());

                    SessionInputBufferImpl sessionInputBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), rawData.length);
                    sessionInputBuffer.bind(new ByteArrayInputStream(rawData));
                    DefaultHttpResponseParser responseParser = new DefaultHttpResponseParser(sessionInputBuffer);
                    HttpResponse response = responseParser.parse();

                    ContentType contentType = null;
                    Header contentTypeHeader = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
                    if (contentTypeHeader != null) {
                        contentType = ContentType.parse(contentTypeHeader.getValue());
                    }
                    byte[] content = new byte[sessionInputBuffer.length()];
                    sessionInputBuffer.read(content);
                    response.setEntity(new ByteArrayEntity(content, contentType));
                    return response;
                }
            } catch (Exception e) {
                LOG.warn("Failed attempt to connect to " + warcUrl.toString());
                if (i == 2) {
                    throw e;
                }
            }
        }

        throw new IOException("Failed connect to " + warcUrl.toString());
    }

    private Map<String, Object> getScrapData(LinkToWarc link, HttpResponse response) {
        Map<String, Object> result = new HashMap<>();

        result.put("id", link.getUrl());
        result.put("url", link.getUrl());
        try {
            result.put("host", new URL(link.getUrl()).getHost());
        } catch (MalformedURLException e) {
            LOG.warn("Cannot extract host from URL " + link.getUrl() + ": " + e.getMessage(), e);
            result.put("host", null);
        }

        try {
            String html = EntityUtils.toString(response.getEntity());
            if (StringUtils.isNotBlank(html)) {
                Document htmlDoc = Jsoup.parse(html, link.getUrl());
                result.put("title", htmlDoc.title());
                Element body = htmlDoc.body();
                if (body == null) {
                    LOG.trace("HTML tag body not found for url " + link.getUrl());
                    return null;
                } else {
                    TextWithLinks textWithLinks = getTextAndLinksFromHtml(body);
                    result.put("content", textWithLinks.getText());
                    result.put("linksWithOffset", GsonHelper.GSON.toJson(textWithLinks.getLinks()));
                }
            } else {
                LOG.trace("Response body is empty for url " + link.getUrl());
                return null;
            }
        } catch (Exception e) {
            LOG.warn("Cannot parse html from http response for url " + link.getUrl() + ": " + e.getMessage(), e);
            return null;
        }

        result.put("digest", link.getDigest());
        result.put("tstamp", link.getTimestamp());
        result.put("processedAt", Instant.now());

        return result;
    }

    private TextWithLinks getTextAndLinksFromHtml(Node node) throws URISyntaxException {
        final StringBuilder accum = new StringBuilder();
        final List<LinkWithOffset> links = new ArrayList<>();

        new NodeTraversor(new NodeVisitor() {
            public void head(Node node, int depth) {
                if (node instanceof TextNode) {
                    TextNode textNode = (TextNode) node;
                    cleanAndAddText(accum, textNode.getWholeText());
                } else if ("a".equalsIgnoreCase(node.nodeName())) {
                    String href = StringUtils.trim(node.attr("href"));
                    if (StringUtils.isNotEmpty(href)) {
                        try {
                            String uriStr = node.attr("abs:href");
                            URI resultUri = new URI(URIUtil.encodeQuery(uriStr));
                            String protocol = resultUri.getScheme();
                            if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {
                                links.add(new LinkWithOffset(uriStr, accum.length()));
                            }
                        } catch (Exception e) {
                            LOG.warn("Failed parse URI: " + href, e);
                        }
                    }
                }
            }

            public void tail(Node node, int depth) {
            }
        }).traverse(node);

        return new TextWithLinks(accum.toString().trim(), links);
    }

    private void cleanAndAddText(StringBuilder sb, String text) {
        if (text != null) {
            text = text.replaceAll("\\s+", " ");
            text = text.trim();
            if (text.length() > 0) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append(text);
            }
        }
    }

    private List<LinkToWarc> filterIfExistsInElastic(TransportClient client, String indexName, List<LinkToWarc> links) {
        if (links.isEmpty()) {
            return links;
        }

        boolean indexExists = client.admin().indices().prepareExists(indexName).execute().actionGet().isExists();
        if (indexExists) {
            MultiGetRequestBuilder builder = client.prepareMultiGet();
            for (LinkToWarc link : links) {
                MultiGetRequest.Item item = new MultiGetRequest.Item(indexName, null, link.getUrl());
                item.fields("tstamp");
                builder.add(item);
            }

            Map<String, ZonedDateTime> oldLinks = new HashMap<>();
            for (MultiGetItemResponse itemResponse : builder.get()) {
                GetResponse response = itemResponse.getResponse();
                if (response != null && response.isExists()) {
                    ZonedDateTime time = ZonedDateTime.parse(String.valueOf(response.getField("tstamp").getValue()));
                    oldLinks.put(response.getId(), time);
                }
            }

            return links.stream().filter(link -> {
                ZonedDateTime oldTime = oldLinks.get(link.getUrl());
                return oldTime == null || oldTime.isBefore(link.getTimestamp());
            }).collect(Collectors.toList());
        } else {
            return links;
        }
    }

    public void stopThread() {
        stop = true;
        this.interrupt();
    }
}
