import { delay } from './util';

export default async () => {
  await page.waitForSelector('#dashboard-onboarding-dialog');
  await page.evaluate(() => {
    document.querySelector('#dashboard-onboarding-dialog .skip-onboarding').click();
  });
};
