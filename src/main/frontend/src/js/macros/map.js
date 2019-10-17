// import $ from 'jquery';
// import _ from 'lodash';
import { loadGraph } from './graph';
import {
  setupServerParam,
  setupMapParam,
} from './general';

async function showMap($div) {
  const $pre = $div.find('pre');

  if (typeof $pre.spin === 'function') {
    $pre.find('span').spin();
  }

  try {
    const data = await loadGraph(
      {
        server: $div.data('server'),
        map: $div.data('map'),
        severity: $div.data('severity'),
      },
      `${AJS.contextPath()}/rest/zabbix-plugin/1.0/image/map`,
    );

    $pre.empty().append($('<img>').attr('src', data));
    if ($div.hasClass('zabbix-plugin-not-licensed')) {
      $div.append($(Mesilat.Zabbix.GeneralTemplates.notLicensedWarning({})));
    }
  } catch (err) {
    $pre.empty().append(`${AJS.I18n.getText('com.mesilat.zabbix-plugin.zabbix-map.label')}: ${err}`);
  }
}

export function showMaps() {
  $('div.zabbix-map').each(function () {
    showMap($(this));
  });
}
export function initMap(selectedParams/* , macroSelected */) {
  setupServerParam(selectedParams);
  setupMapParam(selectedParams);
}
