import $ from 'jquery';
import _ from 'lodash';
import {
  setupServerParam,
  setupHostParam,
  setupGraphParam,
} from './general';

export async function loadGraph(data, url) {
  const deferred = $.Deferred();
  const xhr = new XMLHttpRequest();
  url = url || `${AJS.contextPath()}/rest/zabbix-plugin/1.0/image`;
  const _data = [`_=${Date.now()}`];

  _.keys(data).forEach((key) => {
    _data.push(`${key}=${encodeURIComponent(data[key])}`);
  });
  url = `${url}?${_data.join('&')}`;

  xhr.open('GET', url, true);
  xhr.responseType = 'blob';
  xhr.onload = function (e) {
    if (this.status === 200) {
      deferred.resolve((window.URL || window.webkitURL).createObjectURL(e.target.response));
    } else {
      const reader = new FileReader();
      reader.addEventListener('loadend', el => deferred.reject(el.srcElement.result));
      reader.readAsText(e.target.response);
    }
  };
  xhr.send();
  return deferred.promise();
}

async function showGraph($div) {
  const $pre = $div.find('pre');

  let params;
  if ($div.data('server')) {
    params = {
      server: $div.data('server'),
      host: $div.data('host'),
      graph: $div.data('graph'),
      period: $div.data('period'),
      width: $div.data('width'),
      height: $div.data('height'),
    };
  } else {
    params = {
      'graph-id': $div.data('graph-id'),
      period: $div.data('period'),
      width: $div.data('width'),
      height: $div.data('height'),
    };
  }

  if (typeof $pre.spin === 'function') {
    $pre.find('span').spin();
  }

  try {
    const data = await loadGraph(params);
    $pre.empty().append($('<img>').attr('src', data));
    if ($div.hasClass('zabbix-plugin-not-licensed')) {
      $div.append($(Mesilat.Zabbix.GeneralTemplates.notLicensedWarning({})));
    }
  } catch (err) {
    $pre.empty().append(`${AJS.I18n.getText('com.mesilat.zabbix-plugin.zabbix-graph.label')}: ${err}`);
  }
}

export function showGraphs() {
  $('div.zabbix-graph').each(function () {
    showGraph($(this));
  });
}

export async function initGraph(selectedParams/* , macroSelected */) {
  setupServerParam(selectedParams);
  setupHostParam(selectedParams);
  setupGraphParam(selectedParams);
}
