import {readdir, readFile} from 'node:fs/promises';
import {join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const defaultRoot = fileURLToPath(new URL('../dist/nati-art-frontend/browser/', import.meta.url));
const configuration = await readFile(new URL('../src/environments/environment.production.ts', import.meta.url), 'utf8');
const endpoints = ['directory', 'product'].map((service) => {
  const endpoint = configuration.match(new RegExp(`${service}:\\s*\\{\\s*url:\\s*['"]([^'"]+)['"]`))?.[1];
  if (!endpoint || (!endpoint.startsWith('/server/') && !endpoint.startsWith('https://'))
      || /localhost|127\.0\.0\.1/.test(endpoint)) {
    throw new Error(`Invalid configured production API: ${service}`);
  }
  return endpoint;
});
async function files(directory) {
  const entries = await readdir(directory, {withFileTypes: true});
  const nested = await Promise.all(entries.map((entry) => entry.isDirectory()
    ? files(join(directory, entry.name)) : [join(directory, entry.name)]));
  return nested.flat();
}
export async function verifyProductionArtifacts(root = defaultRoot) {
  for (const locale of ['en', 'pt-BR']) {
    const localeRoot = join(root, locale);
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
    for (const endpoint of endpoints) {
      if (!source.includes(endpoint)) throw new Error(`${locale}: missing production endpoint: ${endpoint}`);
    }
  }
  if ((await files(root)).some((path) => path.endsWith('.map'))) {
    throw new Error('Production build contains source maps');
  }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await verifyProductionArtifacts();
  console.log('Both production locales passed: localized shells, configured APIs, no local APIs or source maps');
}
