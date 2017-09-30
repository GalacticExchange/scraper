### Dependencies
1. Oracle Java 8
    * `sudo add-apt-repository ppa:webupd8team/java`
    * `sudo apt-get update`
    * `sudo apt-get install oracle-java8-installer`
    * `sudo apt-get install oracle-java8-set-default`
2. Maven
    * `sudo apt-get update`
    * `sudo apt-get install maven`
3. Node.js 6.x and npm
    * `curl -sL https://deb.nodesource.com/setup_6.x | sudo -E bash -`
    * `sudo apt-get install -y nodejs`

### Build
1. Go to project folder
2. Run `mvn clean install`
3. Find new build in path `target/scraper.jar`

### Build and deploy 
1. Install Ruby
    * `sudo apt-get update`
    * `sudo apt-get install ruby`
2. Install Rake
    * `sudo gem install rake`
3. Install Ant
    * `sudo apt-get update`
    * `sudo apt-get install ant`
4. Clone repository framework_templates to the same parent folder
5. Clone repository gexcloud as vagrant to the same parent folder
6. Clone repository nutch-fork as nutch to the same parent folder
5. Go to parent_folder/framework_templates/scraper
6. Run `rake build_nutch`
7. Run `rake build_scraper`
7. Run `sudo rake build`
8. Clone appstore-apps to the same parent folder
9. Go to parent_folder/appstore-apps
10. Increment version in /appstore-apps/apps/scraper/build_config.rb
11. Run `gex_env=main rake deploy:upload['scraper']`

### Debug
1. Build project
2. Application needs Consul, ElasticSearch and Nutch REST API running.
    * Consul (https://hub.docker.com/r/progrium/consul/) `docker run -p 8400:8400 -p 8500:8500 -p 8600:53/udp -h node1 progrium/consul -server -bootstrap`
    * ElasticSearch (https://hub.docker.com/r/nshou/elasticsearch-kibana/) `docker run -d -p 9200:9200 -p 9300:9300 -p 5601:5601 nshou/elasticsearch-kibana:kibana4`
    * For Nutch API run scraper docker container.
3. For logs you should create folder with path `/usr/local/scraper` with write permissions to all
4. Run project from main class(io.gex.scraper.api.Main) with two parameters path_to_config and -dev
    * Config file example `{
        "appId":"1234",
        "webServerPort": 4567,
        "consulHost": "localhost",
        "consulPort":  8500,
        "nutchHost": "http://0.0.0.0",
        "nutchPort": 8081,
        "defScrapArchJob": {
            "urls": null,
            "crawlIndexesHost": "http://index.commoncrawl.org",
            "warcFilesHost": "http://commoncrawl.s3.amazonaws.com/",
            "crawlLinksLimit": null,
            "fromYear": 2017,
            "toYear": 2017,
            "fetchThreadsNum": 32,
            "elastic": {
                "host": "0.0.0.0",
                "port": 9300,
                "clusterName": null,
                "indexName": "scraper",
                "type": "scrap_old_data"
            }
        },
        "defScrapJob": {
            "urls": null,
            "depth": 2,
            "interval": 7200,
            "extractArticle": false,
            "elasticIndexName": "scraper"
        }
    }`

5. Go to http://0.0.0.0:3000. By default for debug start up two web servers: java web server on port 4567 and node.js web server on port 3000 which proxy java web server for dynamically adding assets.