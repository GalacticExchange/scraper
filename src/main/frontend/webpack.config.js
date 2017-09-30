const path = require('path');
const fs = require('fs-extra');
const UglifyJSPlugin = require('uglifyjs-webpack-plugin');
const ExtractTextPlugin = require("extract-text-webpack-plugin");
const CopyWebpackPlugin = require('copy-webpack-plugin');

fs.emptyDirSync(path.resolve('../resources/assets'));

module.exports = {
    entry: ['./js/index.js', './styles/main.scss'],
    output: {
        path: path.resolve('../resources/assets/dist/'),
        filename: 'bundle.js',
        publicPath: '/dist/'
    },
    module: {
        rules: [
            {
                test: /\.scss$/,
                use: ExtractTextPlugin.extract({
                    use: [{
                        loader: "css-loader",
                        options: {
                            minimize: true
                        }
                    }, {loader: "sass-loader"}]
                })
            },
            {test: /\.(png|woff|woff2|eot|ttf|svg)$/, loader: 'url-loader?limit=100000'},
            {test: /\.html$/, loader: 'html-loader'}
        ]
    },
    plugins: [
        new UglifyJSPlugin(),
        new ExtractTextPlugin({
            filename: 'bundle.css',
            allChunks: true
        }),
        new CopyWebpackPlugin([
            {from: 'html', to: path.resolve('../resources/assets/html/')},
            {from: 'images', to: path.resolve('../resources/assets/images/')}
        ])
    ]
};