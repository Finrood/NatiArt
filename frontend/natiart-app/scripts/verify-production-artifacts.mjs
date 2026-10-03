import {readdir, readFile} from 'node:fs/promises';
import {join} from 'node:path';

const root = new URL('../dist/nati-art-frontend/browser/', import.meta.url);
async function files(directory) {
  const entries = await readdir(directory, {withFileTypes: true});
  const nested = await Promise.all(entries.map((entry) => entry.isDirectory()
    ? files(join(directory, entry.name)) : [join(directory, entry.name)]));
  return nested.flat();
}
for (const locale of ['en', 'pt-BR']) {
  const localeRoot = join(root.pathname, locale);
  const shell = await readFile(join(localeRoot, 'index.html'), 'utf8');
  if (!shell.includes(`<html lang="${locale}"`) || !shell.includes(`<base href="/${locale}/">`)) {
    throw new Error(`Missing localized production shell: ${locale}`);
  }
  const artifacts = await files(localeRoot);
  if (artifacts.some((path) => path.endsWith('.map'))) throw new Error(`${locale}: production source maps`);
  const scripts = artifacts.filter((path) => path.endsWith('.js'));
  if (!scripts.length) throw new Error(`${locale}: no production JavaScript`);
  const source = (await Promise.all(scripts.map((path) => readFile(path, 'utf8')))).join('\n');
  if (/localhost:808[12]/.test(source)) throw new Error(`${locale}: development API endpoints shipped`);
  for (const endpoint of ['https://natiart.samuelpetre.com/server/directory', 'https://natiart.samuelpetre.com/server/product']) {
    if (!source.includes(endpoint)) throw new Error(`${locale}: missing production endpoint: ${endpoint}`);
  }
}
if ((await files(root.pathname)).some((path) => path.endsWith('.map'))) {
  throw new Error('Production build contains source maps');
}
console.log('Both production locales passed: localized shells, deployed endpoints, no local APIs or source maps');
