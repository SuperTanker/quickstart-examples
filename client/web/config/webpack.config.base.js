const webpack = require('webpack');
const HTMLWebpackPlugin = require('html-webpack-plugin');
const path = require('path');

const browsers = ['last 2 versions', 'Firefox ESR', 'not ie < 11', 'not dead'];

// Use a "ponyfill only" strategy:
//   * using @babel/plugin-transform-runtime (and @babel/runtime-corejs3 at runtime)
//   * avoiding @babel/preset-env with useBuiltIns options (polyfills)
//   * adding a small set of polyfills for IE11 in apps directly
const babelConfig = {
  presets: [
    ['@babel/preset-env', { targets: { browsers } }],
    ['@babel/preset-react'],
  ],
  plugins: [
    ['@babel/plugin-proposal-class-properties'],
    ['@babel/plugin-transform-runtime', { corejs: 3, version: '^7.9.0' }],
  ],
};

const getWebpackConfig = (rootPath) => ({
  devServer: {
    historyApiFallback: true,
    host: '0.0.0.0',
    port: 3000,
    hot: true,
    watchOptions: { ignored: /node_modules/ },
    overlay: true,
  },

  // 'es5' is necessary to support IE
  target: ['web', 'es5'],

  entry: [
    'core-js/features/promise',
    'core-js/features/math/imul',
    path.resolve(rootPath, 'src', 'index.js'),
  ],

  plugins: [
    new HTMLWebpackPlugin({
      template: path.resolve(rootPath, 'public', 'index.html'),
      favicon: path.resolve(rootPath, 'public', 'favicon.ico'),
      filename: 'index.html',
    }),
    // Node.js Polyfills were removed in Webpack 5
    new webpack.ProvidePlugin({
      process: 'process/browser',
    }),
  ],

  module: {
    rules: [
      // Support of CSS imports in JS
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader'],
      },
      // Transpile of our own JS(X) code
      {
        test: /\.js$/,
        loader: 'babel-loader',
        options: babelConfig,
        exclude: /node_modules/,
      },
    ],
  },

  resolve: {
    fallback: {
      // libsodium uses fs for some reason, we don't ever want that in a browser
      fs: false,
      // libsodium never actually uses node's crypto or path in our case
      crypto: false,
      path: false,

      // Node.js polyfills were removed in Webpack 5
      buffer: require.resolve('buffer/'),
      process: require.resolve('process/browser'),
    },
  },
});

module.exports = { getWebpackConfig };
