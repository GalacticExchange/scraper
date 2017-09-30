package io.gex.scraper.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.gex.scraper.api.model.CommandReqBody;
import io.gex.scraper.archive.ScrapArchiveJobExecutor;
import io.gex.scraper.archive.model.ElasticConf;
import io.gex.scraper.archive.model.ScrapArchiveJob;
import io.gex.scraper.common.*;
import io.gex.scraper.current.ScrapJobExecutor;
import io.gex.scraper.current.model.ScrapJob;
import io.gex.scraper.webpack.WebpackThread;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

import static io.gex.scraper.common.GsonHelper.GSON;
import static io.gex.scraper.common.ScrapJobHelper.runScrapJob;
import static spark.Spark.*;


public class WebServer {
    private static final Logger LOG = LoggerFactory.getLogger(WebServer.class);

    private static TemplateEngine templateEngine;

    public static void start() {
        initAssetsPath();
        port(Main.appConf.getWebServerPort());

        if (Boolean.valueOf(System.getProperty("devEnv"))) {
            String projectDir = System.getProperty("user.dir");
            String staticDir = "/src/main/resources/assets";
            staticFiles.externalLocation(projectDir + staticDir);
        } else {
            staticFiles.location("/assets");
        }

        get("/", (request, response) -> {
            Map<String, Object> data = new HashMap<>();
            data.put("scrapArchConf", Main.appConf.getDefScrapArchJob());
            data.put("scrapConf", Main.appConf.getDefScrapJob());
            return renderTemplate("index.html", data);
        });

        get("/scrap/archive/job", "application/json", (request, response) -> {

            return GSON.toJson(ConsulHelper.getAllScrapArchJobs());
        });

        get("/scrap/archive/job/:id", "application/json", (request, response) -> {

            return GSON.toJson(ConsulHelper.getScrapArchJobById(request.params(":id")));
        });

        post("/scrap/archive/job/:id/command", "application/json", (request, response) -> {
            return processCommandForJob(request, response, ScrapArchiveJob.class);
        });

        post("/scrap/job/:id/command", "application/json", (request, response) -> {
            return processCommandForJob(request, response, ScrapJob.class);
        });

        post("/scrap/archive/job", "application/json", (request, response) -> {
            ScrapArchiveJob scrapJob;
            try {
                scrapJob = GSON.fromJson(request.body(), ScrapArchiveJob.class);
            } catch (Exception e) {
                return createErrResp(response, 400, "Cannot parse request body " + e.getMessage(), e);
            }

            try {
                addDefaultData(scrapJob);
                Map<String, String> errors = ScrapArchiveJob.validate(scrapJob);
                if (MapUtils.isNotEmpty(errors)) {
                    return constructValidationError(response, errors);
                }

                scrapJob.init();
                ConsulHelper.setScrapJob(scrapJob);

                runScrapJob(scrapJob);
            } catch (Exception e) {
                return createErrResp(response, 500, e);
            }

            return GSON.toJson(scrapJob);
        });

        delete("/scrap/archive/job/:id", (request, response) -> {
            try {
                ScrapArchiveJob scrapJob = ConsulHelper.getScrapArchJobById(request.params(":id"));
                if (scrapJob == null) {
                    return createErrResp(response, 404, new Exception("Job not found."));
                } else if (scrapJob.isRunning()) {
                    return createErrResp(response, 400, new Exception("Cannot delete running job."));
                }

                new ScrapArchiveJobExecutor(scrapJob).delete();
            } catch (Exception e) {
                return createErrResp(response, 500, e);
            }

            return "{}";
        });


        get("/scrap/job", "application/json", (request, response) -> {

            return GSON.toJson(ConsulHelper.getAllScrapJobs());
        });

        get("/scrap/job/:id", "application/json", (request, response) -> {

            return GSON.toJson(ConsulHelper.getScrapJobById(request.params(":id")));
        });

        post("/scrap/job", "application/json", (request, response) -> {
            ScrapJob scrapJob;
            try {
                scrapJob = GSON.fromJson(request.body(), ScrapJob.class);
            } catch (Exception e) {
                return createErrResp(response, 400, "Cannot parse request body " + e.getMessage(), e);
            }

            try {
                Map<String, String> errors = ScrapJob.validate(scrapJob);
                if (MapUtils.isNotEmpty(errors)) {
                    return constructValidationError(response, errors);
                }

                scrapJob.init();
                ConsulHelper.setScrapJob(scrapJob);

                runScrapJob(scrapJob);
            } catch (Exception e) {
                return createErrResp(response, 500, e);
            }

            return GSON.toJson(scrapJob);
        });

        delete("/scrap/job/:id", (request, response) -> {
            try {
                ScrapJob scrapJob = ConsulHelper.getScrapJobById(request.params(":id"));
                if (scrapJob == null) {
                    return createErrResp(response, 404, new Exception("Job not found."));
                } else if (scrapJob.isRunning()) {
                    return createErrResp(response, 400, new Exception("Cannot delete running job."));
                }

                new ScrapJobExecutor(scrapJob).delete();
            } catch (Exception e) {
                return createErrResp(response, 500, e);
            }

            return "{}";
        });
    }

    private static String processCommandForJob(Request request, Response response, Class<? extends ScrapJobBase> clazz) {
        String id = request.params(":id");
        String command;
        try {
            command = GSON.fromJson(request.body(), CommandReqBody.class).getCommand();
        } catch (Exception e) {
            return createErrResp(response, 400, "Cannot parse request body " + e.getMessage(), e);
        }

        try {
            ScrapJobBaseExecutor executor = ScrapJobHelper.getRunningJob(id);
            if ("stop".equals(command)) {
                if (executor == null) {
                    return createErrResp(response, 400, new Exception("Cannot stop not running job."));
                }

                executor.stop();
                ConsulHelper.setScrapJob(executor.getScrapJob());
            } else if ("start".equals(command)) {
                ScrapJobBase job = ConsulHelper.getScrapJobById(request.params(":id"), clazz);
                if (!job.isRunning()) {
                    runScrapJob(job);
                } else {
                    return createErrResp(response, 400, new Exception("Cannot start running job."));
                }
            } else {
                return createErrResp(response, 400, new Exception("Invalid command " + command + "."));
            }
        } catch (Exception e) {
            return createErrResp(response, 500, e);
        }

        return "{}";
    }

    private static void addDefaultData(ScrapArchiveJob scrapJob) {
        ElasticConf defElastic = Main.appConf.getDefScrapArchJob().getElastic();
        ElasticConf elastic = scrapJob.getElastic();
        elastic.setClusterName(StringUtils.isNotEmpty(elastic.getClusterName()) ? elastic.getClusterName() : defElastic.getClusterName());
        elastic.setHost(StringUtils.isNotEmpty(elastic.getHost()) ? elastic.getHost() : defElastic.getHost());
        elastic.setPort(elastic.getPort() != null ? elastic.getPort() : defElastic.getPort());
        elastic.setIndexName(StringUtils.isNotEmpty(elastic.getIndexName()) ? elastic.getIndexName() : defElastic.getIndexName());
        elastic.setType(StringUtils.isNotEmpty(elastic.getType()) ? elastic.getType() : defElastic.getType());
    }

    private static String constructValidationError(Response res, Map<String, String> errors) {
        res.status(400);
        JsonObject errBase = new JsonObject();
        errBase.addProperty("message", "Fields validation error.");
        JsonArray errs = new JsonArray();
        for (Map.Entry<String, String> entry : errors.entrySet()) {
            JsonObject err = new JsonObject();
            err.addProperty("field", entry.getKey());
            err.addProperty("message", entry.getValue());
            errs.add(err);
        }
        errBase.add("errors", errs);

        return errBase.toString();
    }

    private static String createErrResp(Response res, int status, Exception e) {
        return createErrResp(res, status, e.getMessage(), e);
    }

    private static String createErrResp(Response res, int status, String message, Exception e) {
        LOG.error(message, e);
        res.status(status);
        JsonObject errBase = new JsonObject();
        errBase.addProperty("message", message);

        return errBase.toString();
    }

    private static void initAssetsPath() {
        templateEngine = new org.thymeleaf.TemplateEngine();
        StandardDialect dialect = (StandardDialect) templateEngine.getDialects().stream()
                .filter(d -> d instanceof StandardDialect).findFirst().get();
        dialect.setJavaScriptSerializer(GsonHelper.GSON::toJson);

        if (Boolean.valueOf(System.getProperty("devEnv"))) {
            String projectDir = System.getProperty("user.dir");
            String staticDir = "/src/main/frontend/";
            staticFiles.externalLocation(projectDir + staticDir);

            FileTemplateResolver resolver = new FileTemplateResolver();
            resolver.setSuffix(".html");
            resolver.setPrefix("src/main/frontend/html/");
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCacheable(false);
            templateEngine.setTemplateResolver(resolver);

            WebpackThread.run();
        } else {
            staticFiles.location("/assets");

            final long DEFAULT_CACHE_TTL_MS = 3600000L;
            ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
            templateResolver.setTemplateMode(TemplateMode.HTML);
            templateResolver.setPrefix("assets/html/");
            templateResolver.setSuffix(".html");
            templateResolver.setCacheTTLMs(DEFAULT_CACHE_TTL_MS);
            templateEngine.setTemplateResolver(templateResolver);
        }
    }

    private static String renderTemplate(String viewName, Object model) {
        if (model instanceof Map) {
            Context context = new Context();
            context.setVariables((Map<String, Object>) model);
            return templateEngine.process(viewName, context);
        } else {
            throw new IllegalArgumentException("modelAndView.getModel() must return a java.util.Map");
        }
    }
}
