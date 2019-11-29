/* eslint no-console: 0 */
/* eslint global-require: 0 */
/* eslint import/no-unresolved: 0 */

import $ from 'jquery';
import _ from 'lodash';
import Suggestions from 'suggestions';
// import Autocomplete from './autocomplete';

const DEBUG = true;
export function debug(...args) {
  if (DEBUG) {
    console.debug('ZabbixPlugin', ...args);
  }
}
/*
export function debug() {
  if (DEBUG) {
    const args = ['ZabbixPlugin'];
    for (let i = 0; i < arguments.length; i++) {
      args.push(arguments[i]);
    }
    console.debug.apply(null, args);
  }
}
*/

function getHostSelectorParams() {
  const $hostSelector = $(AJS.Rte.getEditor().contentDocument).find('img.editor-inline-macro[data-macro-name="zabbix-host-selector"]');
  if ($hostSelector.length) {
    let macroParameterSerializer = Confluence.MacroParameterSerializer;
    if (_.isUndefined(macroParameterSerializer)) {
      try {
        macroParameterSerializer = require('macro-params-serializer');// require('confluence-macro-browser/macro-parameter-serializer');
      } catch (err) {
        console.debug('zabbix-plugin', err);
      }
    }
    if (macroParameterSerializer) {
      return macroParameterSerializer.deserialize($($hostSelector[0]).attr('data-macro-parameters'));
    }
  }
  return null;
}
function setManadatory($input) {
  $input.closest('div.macro-param-div')
    .find('label').each(function () {
      const $label = $(this);
      $label.text(`${$label.text()} *`);
    });
}
function toQueryParams(text) {
  if ($('#macro-param-server').val() === '' || $('#macro-param-host').val() === '') {
    return _.extend({}, getHostSelectorParams(), { q: text });
  }
  return {
    server: $('#macro-param-server').val(),
    host: $('#macro-param-host').val(),
    q: text,
  };
}
function setTitle($input, value) {
  if (_.isUndefined(value) || _.isNull(value)) {
    $input.closest('.macro-param-div').find('a.select2-choice').removeAttr('title');
  } else if (_.isObject(value)) {
    $input.closest('.macro-param-div').find('a.select2-choice').attr('title', value.text);
  } else {
    $input.closest('.macro-param-div').find('a.select2-choice').attr('title', value);
  }
}

async function listServers() {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/connection`,
    type: 'GET',
    dataType: 'json',
  });
}
async function getHost(server, host) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/host`,
    type: 'GET',
    data: { server, host },
    dataType: 'json',
  });
}
async function getItem(data) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/items`,
    type: 'GET',
    data,
    dataType: 'json',
  });
}
async function getItemFormats() {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/format`,
    type: 'GET',
    dataType: 'json',
  });
}

export async function setupServerParam(selectedParams) {
  const $input = $('<input type="hidden" class="macro-param-input" id="macro-param-server">');
  if ('server' in selectedParams) {
    $input.val(selectedParams.server);
  }
  $('#macro-param-server').replaceWith($input);

  function convertData(r) {
    const map = {};
    r.forEach((c) => {
      const conn = {
        id: `${c.id}`,
        text: `${c.url} [${c.username}]`,
      };
      if (!(conn.text in map)) {
        map[conn.text] = conn;
      }
    });
    const results = _.values(map);
    results.sort((a, b) => a.text.toLowerCase().localeCompare(b.text.toLowerCase()));
    return results;
  }

  const valueMap = {};
  try {
    let data = await listServers();
    data = convertData(data.results);
    data.forEach((server) => { valueMap[server.id] = server; });
    $input.auiSelect2({
      data,
      formatResult: d => $('<span>').attr('title', d.text).text(d.text),
    });
    if (data.length > 0) {
      $input.closest('.macro-param-div').find('.macro-param-desc').hide();
    }
  } catch (err) {
    console.error('zabbix-plugin', err);
  }

  $input.on('change', () => setTitle($input, valueMap[$input.val()]));
  if (selectedParams && 'server' in selectedParams) {
    $input.val(selectedParams.server).trigger('change');
  }
  setManadatory($input);
  console.debug('zabbix-plugin setupServerParam()');
}
export async function setupHostParam(selectedParams, options) {
  $('#macro-param-host').hide();

  const $input = $('<input type="hidden">');
  $input.insertAfter($('#macro-param-host'));

  function toParams(server, q) {
    return { server, q, 'include-host-groups': !!(options && options.includeHostGroups) };
  }
  function toResults(data) {
    data.results.forEach((d) => {
      if (d.group) {
        d.val = d.text;
        d.id = `group:${d.text}`;
      } else /* host */ {
        d.val = d.id;
        d.id = `host:${d.id}`;
      }
    });
    data.results.sort((a, b) => a.text.toLowerCase().localeCompare(b.text.toLowerCase()));

    const results = options && options.enableSelectAll
      ? [{ id: '__ALL__', text: AJS.I18n.getText('com.mesilat.zabbix-plugin.zabbix-triggers.allHosts') }].concat(data.results)
      : data.results;
    return { results };
  }

  $input.auiSelect2({
    ajax: {
      url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/host`,
      type: 'GET',
      dataType: 'json',
      delay: 250,
      data: searchTerm => toParams($('#macro-param-server').val(), searchTerm),
      results: data => toResults(data),
    },
    minimumInputLength: 0,
    escapeMarkup: markup => markup,
    formatResult: d => $('<span>')
      .attr('title', d.group ? `${AJS.I18n.getText('com.mesilat.zabbix-plugin.common.hostGroup ')}: ${d.text}` : d.text)
      .text(d.text),
  });

  $input.on('change', (e) => {
    const d = e.added;
    if (d) {
      setTitle($input, d.text);
      if (d.id === '__ALL__') {
        $('#macro-param-host').val('__ALL__');
        $('#macro-param-group').val('');
      } else if (d.group) {
        $('#macro-param-host').val(d.val);
        $('#macro-param-group').val(true);
      } else {
        $('#macro-param-host').val(d.val);
        $('#macro-param-group').val('');
      }
    }
  });

  if (selectedParams && 'host' in selectedParams) {
    if (selectedParams.host === '__ALL__') {
      $input.val('__ALL__');
      $input
        .closest('div.macro-param-div')
        .find('span.select2-chosen')
        .text(AJS.I18n.getText('com.mesilat.zabbix-plugin.zabbix-triggers.allHosts'));
      setTitle($input, AJS.I18n.getText('com.mesilat.zabbix-plugin.zabbix-triggers.allHosts'));
    } else if (selectedParams.group) {
      $input.val(`group:${selectedParams.host}`);
      $input
        .closest('div.macro-param-div')
        .find('span.select2-chosen')
        .text(selectedParams.host);
      setTitle($input, selectedParams.host);
    } else {
      $input.val(`host:${selectedParams.host}`);
      try {
        const host = await getHost(selectedParams.server, selectedParams.host);
        $input
          .closest('div.macro-param-div')
          .find('span.select2-chosen')
          .text(host.text);
        setTitle($input, host.text);
      } catch (err) {
        console.error('zabbix-plugin', err);
      }
    }
  }

  setManadatory($input);
}
export async function setupGraphParam(selectedParams) {
  const $input = $('<input type="hidden" class="macro-param-input" id="macro-param-graph">');
  if ('graph' in selectedParams) {
    $input.val(selectedParams.graph);
  }
  $('#macro-param-graph').replaceWith($input);

  function toResults(results) {
    const data = [];
    results.forEach((graph) => {
      data.push({
        id: graph.name,
        text: graph.name,
      });
    });
    data.sort((a, b) => a.text.toLowerCase().localeCompare(b.text.toLowerCase()));
    return { results: data };
  }

  $input.auiSelect2({
    ajax: {
      url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/graph`,
      type: 'GET',
      dataType: 'json',
      delay: 250,
      data: searchTerm => toQueryParams(searchTerm),
      results: data => toResults(data.results),
    },
    minimumInputLength: 0,
    escapeMarkup: markup => markup,
    formatResult: d => $('<span>').attr('title', d.text).text(d.text),
  });

  $input.on('change', () => setTitle($input, $input.val()));
  if ('graph' in selectedParams) {
    $input
      .val(selectedParams.graph)
      .closest('div.macro-param-div').find('span.select2-chosen').text(selectedParams.graph);
    setTitle($input, selectedParams.graph);
  }
  setManadatory($input);
}
export async function setupItemParam(selectedParams) {
  const $input = $('<input type="hidden" class="macro-param-input" id="macro-param-item">');
  if ('item' in selectedParams) {
    $input.val(selectedParams.item);
  }
  $('#macro-param-item').replaceWith($input);

  function toResults(results) {
    const data = [];
    results.forEach((item) => {
      data.push({
        id: item.key,
        text: item.name,
      });
    });
    data.sort((a, b) => a.text.toLowerCase().localeCompare(b.text.toLowerCase()));
    return { results: data };
  }

  $input.auiSelect2({
    ajax: {
      url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/items`,
      type: 'GET',
      dataType: 'json',
      delay: 250,
      data: searchTerm => toQueryParams(searchTerm),
      results: data => toResults(data.results),
    },
    minimumInputLength: 0,
    escapeMarkup: markup => markup,
    formatResult: d => $('<span>').attr('title', d.text).text(d.text),
  });

  if (selectedParams && 'item' in selectedParams) {
    $input.val(selectedParams.item);
    const data = {
      item: selectedParams.item,
    };
    if (!('server' in selectedParams) || !('host' in selectedParams)) {
      _.extend(data, getHostSelectorParams());
    } else {
      data.server = selectedParams.server;
      data.host = selectedParams.host;
    }
    try {
      const item = await getItem(data);
      $input
        .val(item.key)
        .closest('div.macro-param-div').find('span.select2-chosen').text(item.name);
    } catch (err) {
      console.error('zabbix-plugin', err);
    }
  }

  setManadatory($input);
}
export async function setupItemFormatParam() {
  try {
    const formats = {}; const
      names = [];
    (await getItemFormats()).results.forEach((format) => {
      if (!(format.name in formats)) {
        formats[format.name] = `"${format.name}" ${format.format}`;
        names.push(format.name);
      }
    });
    names.sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));

    $('#macro-param-format').each(function () {
      const $input = $(this);
      $input.on('change', () => {
        if ($input.val() in formats) {
          $input.val(formats[$input.val()]);
        }
      });
      return new Suggestions($input[0], names, {
        minLength: 1,
        limit: 3,
      });
    });
  } catch (err) {
    debug(err);
  }
}
export async function setupMapParam(selectedParams) {
  const $input = $('<input type="hidden" class="macro-param-input" id="macro-param-map">');
  if ('map' in selectedParams) {
    $input.val(selectedParams.map);
  }
  $('#macro-param-map').replaceWith($input);

  function toQueryParams2(q) {
    const params = { q };
    if ($('#macro-param-server').val() === '') {
      const selectorParams = getHostSelectorParams();
      if (!_.isUndefined(selectorParams)) {
        params.server = selectorParams.server;
      }
    } else {
      params.server = $('#macro-param-server').val();
    }
    return params;
  }
  function toResults(results) {
    const data = [];
    results.forEach((map) => {
      data.push({
        id: map.name,
        text: map.name,
      });
    });
    data.sort((a, b) => a.text.toLowerCase().localeCompare(b.text.toLowerCase()));
    return { results: data };
  }

  $input.auiSelect2({
    ajax: {
      url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/map`,
      type: 'GET',
      dataType: 'json',
      delay: 250,
      data: searchTerm => toQueryParams2(searchTerm),
      results: data => toResults(data.results),
      minimumInputLength: 0,
      escapeMarkup: markup => markup,
      formatResult: d => $('<span>').attr('title', d.text).text(d.text),
    },
  });
  if ('map' in selectedParams) {
    $input
      .val(selectedParams.map)
      .closest('div.macro-param-div').find('span.select2-chosen').text(selectedParams.map);
  }
  setManadatory($input);
}
