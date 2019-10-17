import {
  setupServerParam,
  setupHostParam,
} from './general';

export function initHostSelector(selectedParams/* , macroSelected */) {
  setupServerParam(selectedParams);
  setupHostParam(selectedParams);
}
