import 'wr-dependency!com.atlassian.auiplugin:ajs';
import 'wr-dependency!com.atlassian.auiplugin:aui-flag';
import 'wr-dependency!com.atlassian.auiplugin:aui-select2';

import 'wr-resource!general.soy.js!templates/general.soy';
import 'wr-resource!triggers.soy.js!templates/triggers.soy';

import macros from './js/macros';

macros();

if (module.hot) {
  module.hot.accept('./js/macros', () => {
    require('./js/macros').default();
  });
}
