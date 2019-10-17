import { showGraphs } from './macros/graph';
import { showMaps } from './macros/map';
import { showItems, showItemsLegacy } from './macros/item';
import { showTriggers, showTriggersLegacy } from './macros/trigger';

function showAll() {
  showGraphs();
  showMaps();
  showItems();
  showItemsLegacy();
  showTriggers();
  showTriggersLegacy();
}

export default () => AJS.toInit(() => {
  showAll();
});
