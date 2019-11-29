import { CONFLUENCE_BASE } from '../.settings';
import { delay } from '../components/util';

export default async (pages) => {
  // Test Map without mandatory "map" param
  await page.goto(`${CONFLUENCE_BASE}/pages/viewpage.action?pageId=${pages['Zabbix Test: Map no param'].id}`);
  await page.waitForSelector('#main-content');
  const errorHeader = await page.evaluate(() => {
    return document.querySelector('#main-content .aui-message-error p.title').textContent;
  });
  expect(errorHeader).toBe('Error rendering macro \'zabbix-map\'');

  const errorMessage = await page.evaluate(() => {
    return document.querySelector('#main-content .aui-message-error p:not(.title)').textContent;
  });
  expect(errorMessage).toBe('Some mandatory parameters are missing');


  // Test Map with "map" = "Local network"
  await page.goto(`${CONFLUENCE_BASE}/pages/viewpage.action?pageId=${pages['Zabbix Test: Map local'].id}`);
  await page.waitForSelector('#main-content');
  const hasSvgMap = await page.evaluate(() => {
    return typeof document.querySelector('#main-content div[data-macro-name="zabbix-map"] svg') !== 'undefined';
  });
  expect(hasSvgMap).toBe(true);
};
