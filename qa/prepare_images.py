"""Build the committed gallery into unique product-owned storage paths."""
import json
from pathlib import Path
import shutil
import sys
import uuid

ROOT = Path(__file__).resolve().parent


def gallery():
    for product, assets in json.loads((ROOT / 'manifest.json').read_text()).items():
        for position, asset in enumerate(assets):
            image_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f'natiart-qa/{product}/{position}/{asset}'))
            yield product, position, asset, f'products/{product}/{image_id}.webp', image_id


def prepare(destination):
    for _, _, asset, key, _ in gallery():
        target = destination / key
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / 'assets' / f'{asset}.webp', target)
    for artwork in json.loads((ROOT / 'artwork.json').read_text()):
        target = destination / artwork['key']
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / 'assets' / (artwork['asset'] + '.webp'), target)


def image_sql():
    statements = ['-- Fixed gallery and LIVE ownership; restored on every QA boot.']
    for product, position, _, key, image_id in gallery():
        uri = 'file:' + key
        statements.append(f"INSERT INTO product_images (product_id, image_position, images) VALUES ('{product}', {position}, '{uri}');")
        statements.append(f"INSERT INTO product_image_ownership (id, uri, product_id, state, created_at, next_attempt_at, cleanup_attempts) VALUES ('{image_id}', '{uri}', '{product}', 'LIVE', CURRENT_TIMESTAMP, TIMESTAMP '1970-01-01 00:00:00', 0);")
    return '\n'.join(statements) + '\n'


if __name__ == '__main__':
    if sys.argv[1:] == ['--sql']:
        print(image_sql(), end='')
    else:
        prepare(Path(sys.argv[1]))
