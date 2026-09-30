#!/usr/bin/env python3
"""Fetch real product HTML through nginx and the application, without JavaScript."""
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'frontend/natiart-app/dist/nati-art-frontend/browser'


def free_port():
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        return listener.getsockname()[1]


def fetch(port, path, status=200, headers=None):
    request = urllib.request.Request(f'http://127.0.0.1:{port}{path}', headers=headers or {})
    try:
        response = urllib.request.urlopen(request, timeout=4)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        assert response.status == status, (path, response.status)
        return response.read().decode(), response.headers


def wait_ready(port, path, process=None):
    for _ in range(60):
        if process is not None and process.poll() is not None:
            raise RuntimeError('Application exited before readiness')
        try:
            fetch(port, path)
            return
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            time.sleep(0.5)
    raise RuntimeError('Local fixture did not become ready')


def main():
    assert (ASSETS / 'en/index.html').is_file(), 'Build the production Angular application first'
    assert '<meta name="natiart-public-metadata">' in (ASSETS / 'en/index.html').read_text()
    jar = next(path for path in (ROOT / 'backend/product-service/build/libs').glob('*.jar')
               if not path.name.endswith('-plain.jar'))
    app_port, edge_port = free_port(), free_port()
    name = 'natiart-ca64-' + uuid.uuid4().hex[:12]
    (ROOT / 'build').mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='ca64-http-', dir=ROOT / 'build') as temporary:
        work = Path(temporary)
        seed = work / 'metadata.sql'
        seed.write_text("""INSERT INTO category(id,version,label,active) VALUES('metadata-category',0,'Fixture',true);
INSERT INTO product(id,version,label,description,original_price,stock_quantity,category_id,active,new_product,featured_product)
VALUES('public-one',0,'Cup "<script>" & café','Actual & public description',10,2,'metadata-category',true,false,false),
('public-two',0,'Second public piece','Distinct second description',20,1,'metadata-category',true,false,false),
('private-one',0,'DO NOT PUBLISH PRIVATE','private upload/customer text',20,1,'metadata-category',false,false,false);
""")
        config = (ROOT / 'frontend/nginx.conf').read_text().replace('listen 80;', f'listen {edge_port};')
        config = config.replace('${PRODUCT_UPSTREAM}', f'http://127.0.0.1:{app_port}')
        config = config.replace('${DIRECTORY_UPSTREAM}', 'http://127.0.0.1:1').replace('${NATIART_PUBLIC_SCHEME}', 'https')
        (work / 'nginx.conf').write_text(config)
        with (work / 'application.log').open('w') as log:
            process = subprocess.Popen(['java', '-jar', str(jar), '--spring.profiles.active=local-h2',
                f'--server.port={app_port}', '--spring.datasource.url=jdbc:h2:mem:metadata-smoke',
                '--spring.jpa.hibernate.ddl-auto=create-drop', f'--spring.sql.init.data-locations=file:{seed}',
                '--natiart.payment.asaas.apikey=inert-metadata-fixture',
                '--natiart.payment.asaas.payments-url=http://127.0.0.1:1/payments',
                '--melhorenvio.api.token=inert-metadata-fixture', '--melhorenvio.api.url=http://127.0.0.1:1/shipping',
                '--directory.service.url=http://127.0.0.1:1'], stdout=log, stderr=log)
            try:
                wait_ready(app_port, '/actuator/health', process)
                subprocess.run(['docker', 'run', '-d', '--name', name, '--network', 'host',
                    '-v', f'{ASSETS}:/usr/share/nginx/html:ro',
                    '-v', f'{work / "nginx.conf"}:/etc/nginx/conf.d/default.conf:ro',
                    'nginx:1.27-alpine'], check=True, stdout=subprocess.DEVNULL)
                wait_ready(edge_port, '/healthz')
                first, headers = fetch(edge_port, '/en/product/public-one', headers={
                    'User-Agent': 'SyntheticShareUnfurler/1.0', 'Cookie': 'inert-fixture-cookie',
                    'Authorization': 'Bearer inert-fixture-token'})
                assert '<title>Cup &#34;&#60;script&#62;&#34; &#38; caf&#233; | NatiArt</title>' in first
                assert '<meta property="og:description" content="Actual &#38; public description">' in first
                assert '<meta property="og:type" content="product">' in first
                assert '<script>"' not in first and 'private upload' not in first
                assert headers['Cache-Control'] == 'no-store'
                second, _ = fetch(edge_port, '/en/product/public-two?tracking=inert')
                assert '<title>Second public piece | NatiArt</title>' in second
                assert 'Distinct second description' in second and 'Actual &#38;' not in second
                for path in ['/en/product/private-one', '/en/product/missing', '/en/product/private-one/']:
                    body, _ = fetch(edge_port, path)
                    assert '<title>NatiArt | Handmade Art</title>' in body
                    assert '<meta property="og:type" content="website">' in body
                    assert 'DO NOT PUBLISH' not in body and 'private upload' not in body
                fetch(edge_port, '/_public-product-metadata', status=404)
                metadata, metadata_headers = fetch(app_port, '/products/public-one/metadata', status=204)
                assert metadata == '' and metadata_headers['X-Natiart-Title'].isascii()
                for language, title, description in [
                        ('en', 'NatiArt | Handmade Art', 'Browse products from NatiArt.'),
                        ('pt-BR', 'NatiArt | Arte artesanal', 'Conhe&#231;a os produtos da NatiArt.')]:
                    body, _ = fetch(edge_port, f'/{language}/product/missing')
                    assert f'<html lang="{language}"' in body
                    assert f'<base href="/{language}/">' in body
                    assert f'<title>{title}</title>' in body
                    assert description in body
                    localized, _ = fetch(edge_port, f'/{language}/product/public-two')
                    assert '<title>Second public piece | NatiArt</title>' in localized
                    assert f'<html lang="{language}"' in localized
                    shell, _ = fetch(edge_port, f'/{language}/login')
                    assert f'<html lang="{language}"' in shell
                fetch(app_port, '/products/public-one/metadata?language=unsupported', status=400)
                legacy, _ = fetch(edge_port, '/product/public-two?tracking=inert')
                assert '<base href="/en/">' in legacy
                print('ok: English and Portuguese initial HTML have accurate language/base/brand and private-safe metadata')
                print('ok: initial HTTP HTML contains distinct escaped active-product metadata; private/missing text stays generic')
                # When the backend disappears, the edge must not serve stale product claims.
                process.terminate(); process.wait(timeout=10)
                fetch(edge_port, '/en/product/public-one', status=500)
                print('ok: metadata subrequest is internal and upstream failure does not reuse stale product text')
            except Exception:
                print((work / 'application.log').read_text()[-12000:])
                subprocess.run(['docker', 'logs', name], check=False)
                raise
            finally:
                subprocess.run(['docker', 'rm', '-fv', name], check=False, stdout=subprocess.DEVNULL)
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill(); process.wait()


if __name__ == '__main__':
    main()
