import $ from 'jquery';
import _ from 'lodash';
import Suggestions from 'suggestions';
//import Autocomplete from './autocomplete';

const DEBUG = true;

export function debug(...args){
  if (DEBUG){
    console.debug(args);
  }
}
function setManadatory($input){
  $input.closest('div.macro-param-div')
  .find('label').each(function () {
    const $label = $(this);
    $label.text($label.text() + " *");
  });
}
function toQueryParams(q){
  if ($('#macro-param-server').val() === '' || $('#macro-param-host').val() === ''){
    return _.extend({}, getHostSelectorParams(), { q });
  } else {
    return {
      'server': $('#macro-param-server').val(),
      'host': $('#macro-param-host').val(),
      q
    };
  }
}
function getHostSelectorParams(){
  const $hostSelector = $(AJS.Rte.getEditor().contentDocument).find('img.editor-inline-macro[data-macro-name="zabbix-host-selector"]');
  if ($hostSelector.length){
    let macroParameterSerializer = Confluence.MacroParameterSerializer;
    if (_.isUndefined(macroParameterSerializer)){
      try {
        macroParameterSerializer = require('macro-params-serializer');// require('confluence-macro-browser/macro-parameter-serializer');
      } catch(err){
        console.debug('zabbix-plugin', err);
      }
    }
    if (macroParameterSerializer){
      return macroParameterSerializer.deserialize($($hostSelector[0]).attr('data-macro-parameters'));
    }
  }
  return null;
}
function setTitle($input, value){
  if (_.isUndefined(value) || _.isNull(value)){
    $input.closest('.macro-param-div').find('a.select2-choice').removeAttr('title');
  } else if (_.isObject(value)){
    $input.closest('.macro-param-div').find('a.select2-choice').attr('title', value.text);
  } else {
    $input.closest('.macro-param-div').find('a.select2-choice').attr('title', value);
  }
}

async function listServers(){
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/connection`,
    type: 'GET',
    dataType: 'json'
  });
}
async function getHost(server, host){
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/host`,
    type: 'GET',
    data: { server, host },
    dataType: 'json'
  });
}
async function getItem(data){
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/items`,
    type: 'GET',
    data: data,
    dataType: 'json'
  });
}
async function getItemFormats(){
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/format`,
    type: 'GET',
    dataType: 'json'
  });
}

export async function setupServerParam(selectedParams) {
  const $input = $('<input type="hidden" class="macro-param-input" id="macro-param-server">');
  if ('server' in selectedParams){
    $input.val(selectedParams.server);
  }
  $('#macro-param-server').replaceWith($input);

  function convertData(r){
    const map = {};
    r.forEach((c) => {
      const conn = {
        id: `${c.id}`,
        text: `${c.url} [${c.username}]`
      };
      if (!(conn.text in map)) {
        map[conn.text] = conn;
      }
    });
    const results = [];
    for (let key in map) {
      results.push(map[key]);
    }
    results.sort((a,b) => a.text.toLowerCase().localeCompare(b.text.toLowerCase()));
    return results;
  }

  const valueMap = {};
  try {
    let data = await listServers();
    data = convertData(data.results);
    data.forEach(server => { valueMap[server.id] = server; });
    $input.auiSelect2({
      data,
      formatResult: (d) => $('<span>').attr('title', d.text).text(d.text)
    });
    if (data.length > 0){
      $input.closest('.macro-param-div').find('.macro-param-desc').hide();
    }
  } catch(err){
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
  const $input = $('<input type="hidden" class="macro-param-input" id="macro-param-host">');
  if ('host' in selectedParams){
    $input.val(selectedParams.host);
  }
  $('#macro-param-host').replaceWith($input);

  function toParams(server, q){
    return { server, q };
  }
  function toResults(data){
    return {
      results: (options && options.enableSelectAll)
        ? [{ id: '__ALL__', text: AJS.I18n.getText('com.mesilat.zabbix-plugin.zabbix-triggers.allHosts') }].concat(data.results)
        : data.results
    };
  }

  $input.auiSelect2({
    ajax: {
      url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/host`,
      type: 'GET',
      dataType: 'json',
      delay: 250,
      data: (searchTerm) => toParams($('#macro-param-server').val(), searchTerm),
      results: (data) => toResults(data)
    },
    minimumInputLength: 0,
    escapeMarkup: (markup) => markup,
    formatResult: (d) => $('<span>').attr('title', d.text).text(d.text)
  });

  $input.on('change', () => setTitle($input, $input.val()));
  if (selectedParams && 'server' in selectedParams && 'host' in selectedParams) {
    try {
      $input.val(selectedParams.host);
      const host = await getHost(selectedParams.server, selectedParams.host);
      $input.val(host.id).closest('div.macro-param-div').find('span.select2-chosen').text(host.text);
      setTitle($input, selectedParams.host);
    } catch(err) {
      console.error('zabbix-plugin', err);
    }
  }
  setManadatory($input);
}
export async function setupGraphParam(selectedParams) {
  const $input = $('<input type="hidden" class="macro-param-input" id="macro-param-graph">');
  if ('graph' in selectedParams){
    $input.val(selectedParams.graph);
  }
  $('#macro-param-graph').replaceWith($input);

  function toResults(results){
    const data = [];
    results.forEach((graph) => {
      data.push({
        id: graph.name,
        text: graph.name
      });
    });
    data.sort((a,b) => a.text.toLowerCase().localeCompare(b.text.toLowerCase()));
    return { results: data };
  }

  $input.auiSelect2({
    ajax: {
      url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/graph`,
      type: 'GET',
      dataType: 'json',
      delay: 250,
      data: (searchTerm) => toQueryParams(searchTerm),
      results: (data) => toResults(data.results)
    },
    minimumInputLength: 0,
    escapeMarkup: (markup) => markup,
    formatResult: (d) => $('<span>').attr('title', d.text).text(d.text)
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
  if ('item' in selectedParams){
    $input.val(selectedParams.item);
  }
  $('#macro-param-item').replaceWith($input);

  function toResults(results){
    const data = [];
    results.forEach((item) => {
      data.push({
        id: item.key,
        text: item.name
      });
    });
    data.sort((a,b) => a.text.toLowerCase().localeCompare(b.text.toLowerCase()));
    return { results: data };
  }

  $input.auiSelect2({
    ajax: {
      url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/items`,
      type: 'GET',
      dataType: 'json',
      delay: 250,
      data: (searchTerm) => toQueryParams(searchTerm),
      results: (data) => toResults(data.results)
    },
    minimumInputLength: 0,
    escapeMarkup: (markup) => markup,
    formatResult: (d) => $('<span>').attr('title', d.text).text(d.text)
  });

  if (selectedParams && 'item' in selectedParams) {
    $input.val(selectedParams.item);
    const data = {
      item: selectedParams.item
    };
    if (!('server' in selectedParams) || !('host' in selectedParams)){
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
    } catch(err) {
      console.error('zabbix-plugin', err);
    }
  }

  setManadatory($input);
}
export async function setupItemFormatParam() {
  try {
    let formats = {}, names = [];
    (await getItemFormats()).results.forEach((format) => {
      if (!(format.name in formats)) {
        formats[format.name] = `"${format.name}" ${format.format}`;
        names.push(format.name);
      }
    });
    names.sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));

    $('#macro-param-format').each(function () {
      const $input = $(this);
      const typeahead = new Suggestions($input[0], names, {
        minLength: 1,
        limit: 3
      });
      $input.on('change', ()=>{
        if ($input.val() in formats){
          $input.val(formats[$input.val()]);
        }
      });
    });
  } catch(err){
    console.error('zabbix-plugin', err);
  }
}
export async function setupMapParam(selectedParams) {
  const $input = $('<input type="hidden" class="macro-param-input" id="macro-param-map">');
  if ('map' in selectedParams){
    $input.val(selectedParams.map);
  }
  $('#macro-param-map').replaceWith($input);

  function toQueryParams(q){
    const params = { q };
    if ($('#macro-param-server').val() === ''){
      const selectorParams = getHostSelectorParams();
      if (!_.isUndefined(selectorParams)){
        params.server = selectorParams.server;
      }
    } else {
      params.server = $('#macro-param-server').val();
    }
    return params;
  }
  function toResults(results){
    const data = [];
    results.forEach((map) => {
      data.push({
        id: map.name,
        text: map.name
      });
    });
    data.sort((a,b) => a.text.toLowerCase().localeCompare(b.text.toLowerCase()));
    return { results: data };
  }

  $input.auiSelect2({
    ajax: {
      url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/map`,
      type: 'GET',
      dataType: 'json',
      delay: 250,
      data: (searchTerm) => toQueryParams(searchTerm),
      results: (data) => toResults(data.results),
      minimumInputLength: 0,
      escapeMarkup: (markup) => markup,
      formatResult: (d) => $('<span>').attr('title', d.text).text(d.text)
    }
  });
  if ('map' in selectedParams) {
    $input
    .val(selectedParams.map)
    .closest('div.macro-param-div').find('span.select2-chosen').text(selectedParams.map);
  }
  setManadatory($input);
}
