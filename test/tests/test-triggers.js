import { CONFLUENCE_BASE } from '../.settings';
import { delay } from '../components/util';

export default async (pages) => {
  // Test Triggers without mandatory "host" param
  await page.goto(`${CONFLUENCE_BASE}/pages/viewpage.action?pageId=${pages['Zabbix Test: Triggers no param'].id}`);
  await page.waitForSelector('#main-content');
  const errorHeader = await page.evaluate(() => {
    return document.querySelector('#main-content .aui-message-error p.title').textContent;
  });
  expect(errorHeader).toBe('Error rendering macro \'zabbix-triggers\'');

  const errorMessage = await page.evaluate(() => {
    return document.querySelector('#main-content .aui-message-error p:not(.title)').textContent;
  });
  expect(errorMessage).toBe('Some mandatory parameters are missing');


  // Test Triggers for all hosts
  await page.goto(`${CONFLUENCE_BASE}/pages/viewpage.action?pageId=${pages['Zabbix Test: Triggers all hosts'].id}`);
  await page.waitForSelector('#main-content');
  await delay(1000); // Some time for async triggers
  let columnCount = await page.evaluate(() => {
    return document.querySelectorAll('#main-content div[data-macro-name="zabbix-triggers"] thead tr th').length;
  });
  expect(columnCount).toBe(5);


  // Test Triggers for oracle hosts
  await page.goto(`${CONFLUENCE_BASE}/pages/viewpage.action?pageId=${pages['Zabbix Test: Triggers oracle group'].id}`);
  await page.waitForSelector('#main-content');
  await delay(1000); // Some time for async triggers
  columnCount = await page.evaluate(() => {
    return document.querySelectorAll('#main-content div[data-macro-name="zabbix-triggers"] thead tr th').length;
  });
  expect(columnCount).toBe(5);


  // Test Triggers for oracle production server
  await page.goto(`${CONFLUENCE_BASE}/pages/viewpage.action?pageId=${pages['Zabbix Test: Triggers host selector'].id}`);
  await page.waitForSelector('#main-content');
  await delay(1000); // Some time for async triggers
  columnCount = await page.evaluate(() => {
    return document.querySelectorAll('#main-content div[data-macro-name="zabbix-triggers"] thead tr th').length;
  });
  expect(columnCount).toBe(3);


/*

  const pageTitle = await page.evaluate(() => {
    return document.querySelector('input#content-title').value;
  });
  expect(pageTitle).toBe(pg.title);

  await page.waitForSelector('iframe#wysiwygTextarea_ifr');
  await delay(1000); // Give some time to init editor
  let text = await page.evaluate(() => {
    return window.getComputedStyle(
      document.querySelector('iframe#wysiwygTextarea_ifr')
      .contentDocument.querySelector('td.validator-email'),
      ':before'
    ).content;
  });
  expect(text).toBe('"Укажите адрес электронной почты"');

  await delay(100);
  await page.evaluate(() => {
    tinymce.activeEditor.selection.select(tinymce.activeEditor.$('td:first-child')[0], true);
  });

  await delay(100);
  await page.keyboard.down('Tab');
  await page.keyboard.up('Tab');

  await delay(100);
  await page.keyboard.type('admin');

  await delay(100);
  await page.keyboard.down('Tab');
  await page.keyboard.up('Tab');

  await delay(100);
  await page.keyboard.down('Tab');
  await page.keyboard.up('Tab');

  await delay(100);
  await page.keyboard.type('admin@home.local');
  await page.keyboard.down('Tab');
  await page.keyboard.up('Tab');

  await delay(100);
  text = await page.evaluate(() => {
    return tinymce.activeEditor.$('td.validator-email')[0].className;
  });
  expect(text).toMatch('is-invalid-email');

  text = await page.evaluate(() => {
    return tinymce.activeEditor.$('td.validator-email')[1].className;
  });
  expect(text).not.toMatch('is-invalid-email');

  //await delay(30000);
  await page.evaluate(() => {
    document.querySelector('button#rte-button-cancel').click();
  });
  await delay(100);
*/
};
