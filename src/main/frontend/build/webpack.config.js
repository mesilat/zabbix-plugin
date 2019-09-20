const path = require('path');
const merge = require('webpack-merge');
const webpack = require('webpack');
const os = require('os');
const WRMPlugin = require('atlassian-webresource-webpack-plugin');
const providedDependencies = require('./providedDependencies');

const PLUGIN_TARGET_DIR = path.join(__dirname, '..', '..', '..', '..', 'target');
const SRC_DIR = path.join(__dirname, '..', 'src');
const OUTPUT_PATH = path.join(PLUGIN_TARGET_DIR, 'classes');

const getWrmPlugin = (watch = false, watchPrepare = false) => {
  return  new WRMPlugin({
    pluginKey: 'com.mesilat.zabbix-plugin',
    xmlDescriptors: path.join(OUTPUT_PATH, 'META-INF', 'plugin-descriptors', 'wr-webpack-bundles.xml'),
    contextMap: {
      'zabbix-macro-view': [ 'page' ],
      'zabbix-macro-edit': [ 'macro-browser' ],
      'zabbix-plugin-settings': []
    },
    providedDependencies: providedDependencies,
    watch: watch,
    watchPrepare: watchPrepare,
  });
};

const webpackConfig = {
  mode: 'development',
  entry: {
    'zabbix-macro-view': path.join(SRC_DIR, 'view.js'),
    'zabbix-macro-edit': path.join(SRC_DIR, 'edit.js'),
    'zabbix-plugin-settings': path.join(SRC_DIR, 'settings.js')
  },
  module: {
    rules: [
      {
        test: /\.jpe?g$/,
        use: [
          {
            loader: 'file-loader',
          },
        ],
      },
      {
        test: /\.less$/,
        use: [
          {
            loader: 'style-loader',
          },
          {
            loader: 'css-loader',
            options: {
              modules: true,
            },
          },
          {
            loader: 'less-loader',
          },
        ],
      },
    ]
  },
  output: {
    path: OUTPUT_PATH,
    filename: '[name].[chunkhash].js',
    chunkFilename: '[name].[chunkhash].js',
    jsonpFunction: 'atlascampjson',
  },
  optimization: {
    splitChunks: false,
    runtimeChunk: false,
  },
  devtool: 'cheap-module-source-map',
  resolve: {
    modules: [
      'node_modules',
      SRC_DIR,
    ],
  },
  plugins: [new webpack.NamedChunksPlugin()]
};

const hostname = os.hostname();
const devServerPort = '3333';

const watchPrepareConfig = {
  output: {
    publicPath: `http://${hostname}:${devServerPort}/`,
    filename: '[name].js',
    chunkFilename: '[name].chunk.js',
  },
  plugins: [
    getWrmPlugin(true, true)
  ],
};

const watchConfig = {
  output: {
    publicPath: `http://${hostname}:${devServerPort}/`,
    filename: '[name].js',
    chunkFilename: '[name].chunk.js',
  },
  devServer: {
    host: hostname,
    port: devServerPort,
    overlay: true,
    hot: true,
    headers: { 'Access-Control-Allow-Origin': '*' },
    disableHostCheck: true
  },
  plugins: [
    new webpack.NamedModulesPlugin(),
    new webpack.HotModuleReplacementPlugin(),
    getWrmPlugin(true),
  ]
};

const devConfig = {
  optimization: {
    splitChunks: {
      minSize: 0,
      chunks: 'all',
      maxInitialRequests: Infinity,
    },
    runtimeChunk: true,
  },
  plugins: [
    getWrmPlugin(),
  ],
}

module.exports = (env) => {
  if (env === "watch:prepare") {
    return merge([webpackConfig, watchPrepareConfig]);
  }

  if (env === "watch") {
    return merge([webpackConfig, watchConfig, watchPrepareConfig]);
  }

  return merge([webpackConfig, devConfig]);
};
