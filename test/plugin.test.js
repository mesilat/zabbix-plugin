/**
 * This test will reuse a running Confluence instance
 */
import _ from 'underscore';
import { CONFLUENCE_BASE, TEST_SPACE } from './.settings';
import { getSpace } from './components/util';
import loginForm from './components/login-form';
import createTestPages from './tests/create-pages';

import testMap from './tests/test-map';
import testTriggers from './tests/test-triggers';

const space = {};
const pages = {};

describe('Zabbix Plugin tests', () => {
  beforeAll(async () => {
    let response = await getSpace(TEST_SPACE);
    if (response.status === 200 && response.data.results.length > 0){
      _.extend(space, response.data.results[0]);
    }
    await createTestPages(space, pages);
    await page.goto(`${CONFLUENCE_BASE}`);
  });
  beforeEach(async () => {
    jest.setTimeout(30000);
  });

  it('check test space exists', () => expect(space.key).toBe(TEST_SPACE));
  it('a user can login using form', loginForm);

  it('test Zabbix Map', () => testMap(pages));
  it('test Zabbix Triggers', () => testTriggers(pages));
});
