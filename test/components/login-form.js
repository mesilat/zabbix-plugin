import { CONFLUENCE_USER, CONFLUENCE_PSWD } from '../.settings.js';
import { delay } from './util';

export default async () => {
  await page.waitForSelector('#login-container');
  await delay(1000); // give some time to init handlers
  await page.evaluate((username, password) => {
    document.querySelector('#os_username').value = username;
    document.querySelector('#os_password').value = password;
  }, CONFLUENCE_USER, CONFLUENCE_PSWD);
  await delay(100);
  await page.evaluate(() => {
    document.querySelector('#loginButton').click();
  });
  await page.waitForSelector('div.confluence-dashboard');
};
