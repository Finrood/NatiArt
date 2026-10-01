import {readdir, readFile} from 'node:fs/promises';
import {join} from 'node:path';

const root = new URL('../dist/nati-art-frontend/browser/', import.meta.url);
async function files(directory) {
  const entries = await readdir(directory, {withFileTypes: true});
  const nested = await Promise.all(entries.map((entry) => entry.isDirectory()
    ? files(join(directory, entry.name)) : [join(directory, entry.name)]));
  return nested.flat();
}
const artifacts = await files(root.pathname);
if (artifacts.some((path) => path.endsWith('.map'))) throw new Error('Production build contains source maps');
const scripts = artifacts.filter((path) => path.endsWith('.js'));
if (!scripts.length) throw new Error('Production build has no JavaScript');
const source = (await Promise.all(scripts.map((path) => readFile(path, 'utf8')))).join('\n');
if (/localhost:808[12]/.test(source)) throw new Error('Development API endpoints shipped');
for (const endpoint of ['https://natiart.samuelpetre.com/server/directory', 'https://natiart.samuelpetre.com/server/product']) {
  if (!source.includes(endpoint)) throw new Error(`Missing production endpoint: ${endpoint}`);
}
console.log('Production artifact policy passed: deployed endpoints, no local APIs or source maps');
