import {test} from 'node:test';
import assert from 'node:assert/strict';
import {cp, mkdtemp, rm, writeFile, readdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {verifyProductionArtifacts} from './verify-production-artifacts.mjs';

const actual = fileURLToPath(new URL('../dist/nati-art-frontend/browser/', import.meta.url));
test('actual production artifacts satisfy the configured relative API contract', async () => {
  await verifyProductionArtifacts(actual);
});
for (const scenario of ['missing locale', 'source map', 'development URL', 'missing configured API']) {
  test(`actual artifact copy rejects ${scenario}`, async () => {
    const temporary = await mkdtemp(join(tmpdir(), 'natiart-artifact-contract-'));
    try {
      const artifacts = join(temporary, 'browser');
      await cp(actual, artifacts, {recursive: true});
      if (scenario === 'missing locale') await rm(join(artifacts, 'pt-BR'), {recursive: true});
      if (scenario === 'source map') await writeFile(join(artifacts, 'en', 'leaked.js.map'), '{}');
      if (scenario === 'development URL') await writeFile(join(artifacts, 'en', 'development.js'), '"http://localhost:8081"');
      if (scenario === 'missing configured API') {
        for (const name of await readdir(join(artifacts, 'en'))) {
          if (name.endsWith('.js')) await writeFile(join(artifacts, 'en', name), '/* missing API configuration */');
        }
      }
      await assert.rejects(verifyProductionArtifacts(artifacts));
    } finally {
      await rm(temporary, {recursive: true, force: true});
    }
  });
}
