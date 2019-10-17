import { initGraph } from './macros/graph';
import { initItem } from './macros/item';
import { initTrigger } from './macros/trigger';
import { initMap } from './macros/map';
import { initHostSelector } from './macros/selector';

function initAll() {
  AJS.MacroBrowser.setMacroJsOverride('zabbix-graph', {
    beforeParamsSet: (selectedParams, macroSelected) => {
      const selectedParamsCopy = _.extend({}, selectedParams);
      initGraph(selectedParamsCopy, macroSelected);
      return selectedParams;
    },
  });
  AJS.MacroBrowser.setMacroJsOverride('zabbix-item', {
    beforeParamsSet: (selectedParams, macroSelected) => {
      const selectedParamsCopy = _.extend({}, selectedParams);
      initItem(selectedParamsCopy, macroSelected);
      return selectedParams;
    },
  });
  AJS.MacroBrowser.setMacroJsOverride('zabbix-triggers', {
    beforeParamsSet: (selectedParams, macroSelected) => {
      const selectedParamsCopy = _.extend({}, selectedParams);
      initTrigger(selectedParamsCopy, macroSelected);
      return selectedParams;
    },
  });
  AJS.MacroBrowser.setMacroJsOverride('zabbix-map', {
    beforeParamsSet: (selectedParams, macroSelected) => {
      const selectedParamsCopy = _.extend({}, selectedParams);
      initMap(selectedParamsCopy, macroSelected);
      return selectedParams;
    },
  });
  AJS.MacroBrowser.setMacroJsOverride('zabbix-host-selector', {
    beforeParamsSet: (selectedParams, macroSelected) => {
      const selectedParamsCopy = _.extend({}, selectedParams);
      initHostSelector(selectedParamsCopy, macroSelected);
      return selectedParams;
    },
    // beforeParamsRetrieved: (params) => params
  });
}

export default () => {
  initAll();
};
