import { CONFLUENCE_BASE, CONFLUENCE_USER, CONFLUENCE_PSWD } from '../.settings.js';
import { exec } from 'child_process';
import axios from 'axios';
import kill from 'tree-kill';

export async function delay(time) {
   return new Promise((resolve) => {
       setTimeout(resolve, time)
   });
}

export async function waitForConfluenceToStart() {
  let text = [];
  return new Promise((resolve, reject) => {
    console.debug('Wait for Confluence to start...');
    const server = exec('atlas-run', {
      cwd: `${process.cwd()}/..`,
      shell: '/bin/bash',
    });
    server.stdout.on('data', (data) => {
      const d = data.split(/\r?\n/);
      if (d.length > 0){
        text.push(d[0]);
      }
      for (let i = 1; i < d.length; i++){
        if (text.join('').indexOf('confluence started successfully') >= 0) {
          console.debug('Confluence server started');
          resolve(server);
        }
        text = [];
        text.push(d[i]);
      }
    });
    server.stderr.on('data', (data) => {});
    server.on('close', (code) => {
      reject();
    });
  });
}

export async function waitForConfluenceToStop(server) {
  server.kill();
  return new Promise((resolve) => {
    const server = exec("ps -ef | grep '1990/confluence' | grep -v grep | awk '{print $2}'", (err, stdout, stderr) => {
      kill(stdout.trim(), 'SIGKILL');
      console.debug('Confluence server terminated');
      resolve();
    });
  });
}

export async function getSpace(spaceKey) {
  const url = `${CONFLUENCE_BASE}/rest/api/space?spaceKey=${spaceKey}&expand=homepage`;
  return axios.get(url,
    {
      auth: {
        username: CONFLUENCE_USER,
        password: CONFLUENCE_PSWD
      },
    }
  );
}
export async function getPage(spaceKey, title) {
  const url = `${CONFLUENCE_BASE}/rest/api/content?spaceKey=${spaceKey}&title=${encodeURIComponent(title)}`;
  return axios.get(url,
    {
      auth: {
        username: CONFLUENCE_USER,
        password: CONFLUENCE_PSWD
      },
    }
  );
}
export async function postPage(spaceKey, title, body) {
  const url = `${CONFLUENCE_BASE}/rest/api/content`;
  return axios.post(url,
    {
      type: 'page',
      title: title,
      space:{
        key: spaceKey
      },
      body: {
        storage: {
          value: body,
          representation: 'storage'
        }
      }
    },
    {
      auth: {
        username: CONFLUENCE_USER,
        password: CONFLUENCE_PSWD
      },
      headers: {
        'Content-Type': 'application/json',
        'x-atlassian-token': 'no-check'
      }
    }
  );
}
export async function putPageTitleAndBody(pageId, version, title, body) {
  const url = `${CONFLUENCE_BASE}/rest/api/content/${pageId}`;
  return axios.put(url,
    {
      id: `${pageId}`,
      type: 'page',
      title: title,
      version: {
        number: version
      },
      body: {
        storage: {
          value: body,
          representation: 'storage'
        }
      }
    },
    {
      auth: {
        username: CONFLUENCE_USER,
        password: CONFLUENCE_PSWD
      },
      headers: {
        'Content-Type': 'application/json',
        'x-atlassian-token': 'no-check'
      }
    }
  );
}
