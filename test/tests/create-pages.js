import fs from 'fs';
import * as SETTINGS from '../.settings';
import { delay, postPage, getPage } from '../components/util';

export default async (space, pages) => {
  async function addPage(title, xml){
    try {
      const pageBody = fs.readFileSync(xml, 'utf8');
      const body = pageBody.replace(/\$\{(.+?)\}/g, (g0, g1) => SETTINGS[g1]);
      const response = await postPage(space.key, title, body);
      expect(response.status).toBe(200);
      pages[title] = response.data;
    } catch(err) {
      if (err.response.data.message.indexOf('page already exists') >= 0){
        const response = await getPage(space.key, title);
        if (response.data.results.length > 0) {
          pages[title] = response.data.results[0];
        }
      } else {
        console.error(`${title}: ${err.response.data.message}`);
      }
    }
  }

  await addPage('Zabbix Test: Map no param', 'pages/test-zabbix-map-noparam.xml');
  await addPage('Zabbix Test: Map local', 'pages/test-zabbix-map-local.xml');

  await addPage('Zabbix Test: Triggers no param', 'pages/test-zabbix-trigger-noparam.xml');
  await addPage('Zabbix Test: Triggers all hosts', 'pages/test-zabbix-trigger-allhosts.xml');
  await addPage('Zabbix Test: Triggers oracle group', 'pages/test-zabbix-trigger-oracle.xml');
  await addPage('Zabbix Test: Triggers host selector', 'pages/test-zabbix-trigger-host-selector.xml');
};
