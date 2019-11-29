import _ from 'underscore';
import { CONFLUENCE_BASE } from '../.settings.js';
import { delay, putPageTitleAndBody } from './util';

export async function createPage(title, body){
  await page.goto(`${CONFLUENCE_BASE}/display/TS`);

  await page.waitForSelector('#quick-create-page-button');
  await page.evaluate(() => {
    document.querySelector('#quick-create-page-button').click();
  });
  await page.waitForSelector('#content-title');
  await page.evaluate((title) => {
    document.querySelector('#content-title').value = title;
  }, title);
  await page.waitForSelector('#rte-button-publish');
  await page.evaluate(() => {
    document.querySelector('#rte-button-publish').click();
  });
  await delay(3000);
  await page.waitForSelector('h1#title-text');
  const pageTitle = await page.evaluate(() => {
    return document.querySelector('h1#title-text').innerText;
  });
  expect(pageTitle).toBe(title);

  await page.waitForSelector('meta[name="ajs-page-id"]');
  const pageId = await page.evaluate(() => {
    return document.querySelector('meta[name="ajs-page-id"]').content;
  });
  expect(pageId).not.toBeUndefined();

  if (!_.isUndefined(pageId)){
    try {
      const result = await putPageTitleAndBody(pageId, 2, title, body);
      expect(result.statusText).toBe('OK');
    } catch(err) {
      console.log(err);
    }

    await page.goto(`${CONFLUENCE_BASE}/pages/viewpage.action?pageId=${pageId}`);
    await delay(1000);
    await page.waitForSelector('div#main-content');
  }
};
