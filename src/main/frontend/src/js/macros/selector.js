import $ from 'jquery';
import _ from 'lodash';
import {
  setupServerParam,
  setupHostParam
} from './general';

export function initHostSelector(selectedParams, macroSelected) {
  setupServerParam(selectedParams);
  setupHostParam(selectedParams);
}
