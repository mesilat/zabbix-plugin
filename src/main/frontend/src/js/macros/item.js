import $ from 'jquery';
// import _ from 'lodash';
import {
  debug,
  setupServerParam,
  setupHostParam,
  setupItemParam,
  setupItemFormatParam,
} from './general';

async function loadItemsLegacy(items) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/items/legacy`,
    type: 'POST',
    contentType: 'application/json',
    data: JSON.stringify(items.obj),
    processData: false,
    dataType: 'json',
  });
}
async function loadItemsFromZabbixServer(items, server) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/items`,
    type: 'POST',
    contentType: 'application/json',
    data: JSON.stringify(items[server].obj),
    processData: false,
    dataType: 'json',
    context: items[server],
  });
}

function showError($elt, message) {
  $elt.spinStop();
  $('<a href="#"></a>')
    .text(AJS.I18n.getText('com.mesilat.zabbix-plugin.common.error'))
    .appendTo($elt)
    .attr('title', message)
    .tooltip();
}

async function showItemsForServer(items, server) {
  try {
    const data = await loadItemsFromZabbixServer(items, server);
    if (data.results) {
      for (let i = 0; i < items[server].elt.length; i++) {
        const $elt = items[server].elt[i];
        $elt.spinStop();
        if (data.results.length > i) {
          $elt.html(data.results[i]);
        }
      }
    } else {
      debug('zabbix-plugin', 'Invalid data received from REST service', data);
      items[server].elt.forEach(($elt) => {
        $elt.spinStop();
        showError($elt, AJS.I18n.getText('com.mesilat.zabbix-plugin.error.unexpected'));
      });
    }
  } catch (err) {
    items[server].elt.forEach(($elt) => {
      $elt.spinStop();
      showError($elt, jqXHR.responseText);
    });
  }
}

export function showItems() {
  const items = {};
  $('.zabbix-item2').each(function () {
    const $elt = $(this);
    const obj = {
      server: $elt.attr('server'),
      host: $elt.attr('host'),
      item: $elt.attr('item'),
      format: $elt.attr('format'),
      token: $elt.attr('token'),
    };
    if (!(obj.server in items)) {
      items[obj.server] = {
        obj: [],
        elt: [],
      };
    }
    items[obj.server].obj.push(obj);
    items[obj.server].elt.push($elt);
    $elt.spin();
  });
  // Requests per server
  _.keys(items).forEach(server => showItemsForServer(items, server));
}
export async function showItemsLegacy() {
  const items = {
    obj: [],
    elt: [],
  };
  $('.zabbix-item').each(function () {
    const $item = $(this);
    items.obj.push({
      itemId: $item.attr('item-id'),
      format: $item.attr('format'),
      token: $item.attr('token'),
    });
    items.elt.push($item);
    $item.spin();
  });

  if (items.obj.length) {
    try {
      const data = await loadItemsLegacy(items);

      if (data.results) {
        for (let i = 0; i < items.elt.length; i++) {
          const $elt = items.elt[i];
          $elt.spinStop();
          if (data.results.length > i) {
            $elt.html(data.results[i]);
          }
        }
      } else {
        debug('zabbix-plugin', 'Invalid data received from REST service', data);
        items.elt.forEach(($elt) => {
          $elt.spinStop();
          showError($elt, AJS.I18n.getText('com.mesilat.zabbix-plugin.error.unexpected'));
        });
      }
    } catch (err) {
      items.elt.forEach(($elt) => {
        $elt.spinStop();
        showError($elt, err.responseText);
      });
    }
  }
}
export function initItem(selectedParams/* , macroSelected */) {
  setupServerParam(selectedParams);
  setupHostParam(selectedParams);
  setupItemParam(selectedParams);
  setupItemFormatParam(selectedParams);
}
