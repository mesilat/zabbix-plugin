import 'wr-dependency!com.atlassian.auiplugin:ajs';
import 'wr-dependency!com.atlassian.auiplugin:aui-flag';
import 'wr-dependency!com.atlassian.confluence.plugins.confluence-ui-components:user-group-select2';
import 'wr-resource!settings.soy.js!templates/settings.soy';

import settings from './js/settings';

settings();

if (module.hot) {
  module.hot.accept('./js/settings', () => {
    require('./js/settings').default();
  });
}
