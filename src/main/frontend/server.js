const http = require('http');
const express = require('express');
const httpProxy = require('http-proxy');
const path = require('path');

const proxy = httpProxy.createProxyServer({});

const app = express();

app.use(require('morgan')('short'));

(function initWebpack() {
    const webpack = require('webpack');
    const webpackConfig = require('./webpack.test.config');

    const compiler = webpack(webpackConfig);

    app.use(require('webpack-dev-middleware')(compiler, {
        noInfo: true, publicPath: webpackConfig.output.publicPath
    }));

    app.use(require('webpack-hot-middleware')(compiler, {
        log: console.log, path: '/__webpack_hmr', heartbeat: 10 * 1000
    }));

    app.use(express.static(path.join(__dirname, '/')));
}());

app.all(/.*/, function (req, res) {
    proxy.web(req, res, {target: 'http://0.0.0.0:4567'})
});

const server = http.createServer(app);
server.listen(process.env.PORT || 3000, function () {
    const address = server.address();

    console.log('Listening on: %j', address);
    console.log(' -> that probably means: http://localhost:%d', address.port);
});
