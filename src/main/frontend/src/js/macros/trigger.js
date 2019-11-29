import $ from 'jquery';
// import _ from 'lodash';
import {
  setupServerParam,
  setupHostParam,
  debug,
} from './general';

async function getEvents(server, trigger) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/events`,
    type: 'GET',
    data: {
      server,
      trigger,
    },
    dataType: 'json',
  });
}
async function getTriggers(data) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/triggers`,
    type: 'GET',
    data,
    dataType: 'json',
    context: data,
  });
}

async function loadTriggersLegacy(triggers) {
  return $.ajax({
    url: `${AJS.contextPath()}/rest/zabbix-plugin/1.0/triggers/legacy`,
    type: 'POST',
    contentType: 'application/json',
    data: JSON.stringify({
      token: triggers.elt[0].attr('token'),
      hostIds: triggers.obj,
    }),
    processData: false,
    dataType: 'json',
    context: triggers,
  });
}

function showError($elt, message) {
  $('<a href="#"></a>')
    .text(AJS.I18n.getText('com.mesilat.zabbix-plugin.common.error'))
    .appendTo($elt)
    .attr('title', message)
    .tooltip();
}

export function showTriggers() {
  $('.zabbix-triggers2').each(async function () {
    const $elt = $(this);
    const server = parseInt($elt.attr('server'), 10);
    const token = $elt.attr('token');
    const query = { server, token };
    if ($elt.attr('host')) {
      query.host = $elt.attr('host');
    }
    if ($elt.attr('group')) {
      query['host-group'] = $elt.attr('group');
    }
    const showHostAndGroup = query['host-group'] || query.host === '__ALL__';

    try {
      $elt.spin();
      const triggers = await getTriggers(query);
      console.log('showTriggers()', triggers);
      $elt.empty().append(
        triggers.length === 0
          ? $(Mesilat.Zabbix.TriggersTemplates.zabbixAllGood({}))
          : $(Mesilat.Zabbix.TriggersTemplates.zabbixTriggers({ triggers, showHostAndGroup })),
      );
      if ($elt.hasClass('zabbix-plugin-not-licensed')) {
        $elt.append($(Mesilat.Zabbix.GeneralTemplates.notLicensedWarning({})));
      }

      $elt.on('click', 'a', async function (e) {
        const $a = $(this);
        if ($a.attr('href') === 'javascript:0;') {
          e.preventDefault();

          const trigger = $a.closest('tr').data('trigger-id');
          if (typeof trigger !== 'undefined') {
            try {
              const events = await getEvents(server, trigger);
              if (events.results.length === 0) {
                throw new Error(AJS.I18n.getText('com.mesilat.zabbix-plugin.error.no-events'));
              }
              $a
                .attr('href', events.results[0].url)
                .attr('target', '_blank')[0].click();
            } catch (err) {
              (AJS.flag || window.require('aui/flag'))({
                type: 'error',
                title: AJS.I18n.getText('com.mesilat.zabbix-plugin.common.error'),
                body: err.responseText || err.message,
              });
            }
          }
        }
      });
    } catch (err) {
      debug('::showTriggers()', err);
      showError($elt, err.responseText);
    } finally {
      $elt.spinStop();
    }
  });
}
export async function showTriggersLegacy() {
  const triggers = {
    obj: [],
    elt: [],
  };
  $('.zabbix-triggers').each(function () {
    const $elt = $(this);
    triggers.obj.push($elt.attr('host-id'));
    triggers.elt.push($elt);
    $elt.spin();
  });

  if (!triggers.obj.length) {
    return;
  }

  try {
    const data = await loadTriggersLegacy(triggers);

    for (let i = 0; i < triggers.elt.length; i++) {
      const $elt = triggers.elt[i];
      $elt.spinStop();
      const trg = data.results[triggers.obj[i]];
      if (typeof trg !== 'undefined') {
        let $template;
        if (trg.length === 0) {
          $template = $(Mesilat.Zabbix.TriggersTemplates.zabbixAllGood({}));
        } else {
          $template = $(Mesilat.Zabbix.TriggersTemplates.zabbixTriggersSync({
            triggers: trg,
          }));
        }
        $elt.empty().append($template);
        if ($elt.hasClass('zabbix-plugin-not-licensed')) {
          $template = $(Mesilat.Zabbix.GeneralTemplates.notLicensedWarning({}));
          $elt.append($template);
        }
      } else {
        showError($elt, AJS.I18n.getText('com.mesilat.zabbix-plugin.error.unexpected'));
      }
    }
  } catch (err) {
    debug('zabbix-plugin triggers-legacy', err.responseText);
    triggers.elt.forEach(($elt) => {
      $elt.spinStop();
      showError($elt, err.responseText);
    });
  }
}

export async function initTrigger(selectedParams/* , macroSelected */) {
  // console.log('zabbix-plugin::initTrigger()', selectedParams);
  setupServerParam(selectedParams);
  setupHostParam(selectedParams, { enableSelectAll: true, includeHostGroups: true });
}
