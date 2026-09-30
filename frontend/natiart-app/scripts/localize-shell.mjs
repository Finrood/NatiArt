import {readFile, writeFile, access, copyFile} from 'node:fs/promises';
import {constants} from 'node:fs';

const root = new URL('../dist/nati-art-frontend/browser/', import.meta.url);
const copy = {
  en: ['NatiArt | Handmade Art', 'Browse products from NatiArt.'],
  'pt-BR': ['NatiArt | Arte artesanal', 'Conheça os produtos da NatiArt.'],
};
for (const [language, [title, description]] of Object.entries(copy)) {
  const index = new URL(`${language}/index.html`, root);
  try { await access(index, constants.F_OK); } catch { continue; }
  let html = await readFile(index, 'utf8');
  html = html.replace(/<title>[^<]*<\/title>/, `<title>${title}</title>`)
    .replace(/<meta name="description" content="[^"]*">/, `<meta name="description" content="${description}">`)
    .replace(/<meta name="content-language" content="[^"]*">/, `<meta name="content-language" content="${language}">`);
  await writeFile(index, html);
}

await copyFile(new URL('../public/runtime-config.js', import.meta.url), new URL('runtime-config.js', root));
