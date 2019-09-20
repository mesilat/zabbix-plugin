import $ from 'jquery';
import _ from 'lodash';
import {
  setupServerParam,
  setupHostParam
} from './general';

async function loadEvents(server, trigger){
  return $.ajax({
    url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/events',
    type: 'GET',
    data: {
      server: server,
      trigger: trigger
    },
    dataType: 'json'
  });
}
async function loadTriggers(server, triggers){
  return $.ajax({
    url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/triggers',
    type: 'POST',
    contentType: 'application/json',
    data: JSON.stringify({
      token: triggers.elt[0].attr('token'),
      server: server,
      hosts: triggers.obj
    }),
    processData: false,
    dataType: 'json',
    context: triggers
  });
}
async function loadTriggersLegacy(triggers){
  return $.ajax({
    url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/triggers/legacy',
    type: 'POST',
    contentType: 'application/json',
    data: JSON.stringify({
      token: triggers.elt[0].attr('token'),
      hostIds: triggers.obj
    }),
    processData: false,
    dataType: 'json',
    context: triggers
  });
}

function showError($elt, message){
  $('<a href="#"></a>')
  .text(AJS.I18n.getText("com.mesilat.zabbix-plugin.common.error"))
  .appendTo($elt)
  .attr('title', message)
  .tooltip();
}

async function showTriggersForServer(server, triggers){
  try {
    const data = await loadTriggers(server, triggers);

    for (var i = 0; i < triggers.elt.length; i++) {
      var $elt = triggers.elt[i];
      $elt.spinStop();
      var trg = data.results[triggers.obj[i]];
      if (typeof trg !== 'undefined') {
        var $template;
        if (trg.length === 0) {
          $template = $(Mesilat.Zabbix.TriggersTemplates.zabbixAllGood({}));
        } else if ($elt.attr('host') === '__ALL__') {
          $template = $(Mesilat.Zabbix.TriggersTemplates.zabbixTriggersAll({
            triggers: trg
          }));
        } else {
          $template = $(Mesilat.Zabbix.TriggersTemplates.zabbixTriggersSync({
            triggers: trg
          }));
        }
        $elt.empty().append($template);
        if ($elt.hasClass('zabbix-plugin-not-licensed')) {
          $template = $(Mesilat.Zabbix.GeneralTemplates.notLicensedWarning({}));
          $elt.append($template);
        }
      } else {
        showError($elt, AJS.I18n.getText("com.mesilat.zabbix-plugin.error.unexpected"));
      }
    }
  } catch(err) {
    console.error('zabbix-plugin triggers', err);
    triggers.elt.forEach(($elt) => {
      $elt.spinStop();
      showError($elt, err.responseText);
    });
  }
}

export function showTriggers() {
  var triggers = {};
  $('.zabbix-triggers2').each(function () {
    var $elt = $(this);
    var server = $elt.attr('server');
    var host = $elt.attr('host');
    if (!(server in triggers)) {
      triggers[server] = {
        obj: [],
        elt: []
      };
    }
    triggers[server].obj.push(host);
    triggers[server].elt.push($elt);

    $elt.spin();
    $elt.on('click', 'a', async function(e){
      var $a = $(this);
      if ($a.attr('href') === 'javascript:0;'){
        e.preventDefault();
        var trigger = $a.closest('tr').data('trigger-id');
        if (typeof trigger !== 'undefined'){
          try {
            const data = await loadEvents(server, trigger);
            $a
            .attr('href', data.results[0].url)
            .attr('target', '_blank')
            [0].click();
          } catch(err){
            // TODO: aui flag
            console.error('zabbix-plugin events', err.responseText);
          }
        }
      }
    });
  });

  // Requests per server
  for (var server in triggers) {
    showTriggersForServer(server, triggers[server]);
  }
}
export async function showTriggersLegacy() {
  var triggers = {
    obj: [],
    elt: []
  };
  $('.zabbix-triggers').each(function () {
    var $elt = $(this);
    triggers.obj.push($elt.attr('host-id'));
    triggers.elt.push($elt);
    $elt.spin();
  });

  if (!triggers.obj.length) {
    return;
  }

  try {
    const data = await loadTriggersLegacy(triggers);

    for (var i = 0; i < triggers.elt.length; i++) {
      var $elt = triggers.elt[i];
      $elt.spinStop();
      var trg = data.results[triggers.obj[i]];
      if (typeof trg !== 'undefined') {
        var $template;
        if (trg.length === 0) {
          $template = $(Mesilat.Zabbix.TriggersTemplates.zabbixAllGood({}));
        } else {
          $template = $(Mesilat.Zabbix.TriggersTemplates.zabbixTriggersSync({
            triggers: trg
          }));
        }
        $elt.empty().append($template);
        if ($elt.hasClass('zabbix-plugin-not-licensed')) {
          $template = $(Mesilat.Zabbix.GeneralTemplates.notLicensedWarning({}));
          $elt.append($template);
        }
      } else {
        showError($elt, AJS.I18n.getText("com.mesilat.zabbix-plugin.error.unexpected"));
      }
    }
  } catch(err){
    console.error('zabbix-plugin triggers-legacy', err.responseText);
    triggers.elt.forEach(($elt) => {
      $elt.spinStop();
      showError($elt, err.responseText);
    });
  }
}
export function initTrigger(selectedParams, macroSelected) {
  setupServerParam(selectedParams);
  setupHostParam(selectedParams, { enableSelectAll: true });
}
