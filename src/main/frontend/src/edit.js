import 'wr-dependency!com.atlassian.auiplugin:ajs';
import 'wr-dependency!com.atlassian.auiplugin:aui-flag';
import 'wr-dependency!com.atlassian.auiplugin:aui-select2';

import 'wr-resource!general.soy.js!templates/general.soy';
import 'wr-resource!triggers.soy.js!templates/triggers.soy';

import browser from './js/browser';

browser();

if (module.hot) {
  module.hot.accept('./js/browser', () => {
    require('./js/browser').default();
  });
}
