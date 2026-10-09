#!/usr/bin/env python3
"""Exercise the shipped nginx template/entrypoint with two immutable releases."""
import argparse
import http.client
import json
import pathlib
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

FRONTEND = pathlib.Path(__file__).resolve().parents[1]


def docker(*args):
    return subprocess.check_output(['docker', *args], text=True, stderr=subprocess.STDOUT).strip()


def request(port, path, data=None, headers=None):
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=data, headers=headers or {})
    try:
        response = urllib.request.urlopen(req, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read().decode()


def ready(port):
    for _ in range(100):
        try:
            if request(port, '/healthz')[0] == 200:
                return
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(0.1)
    raise AssertionError('Container did not become ready')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--interactive', action='store_true', help='Pause for an old-tab browser probe')
    args = parser.parse_args()
    prefix = 'natiart-ca49-test-' + uuid.uuid4().hex[:10]
    network, volume = prefix + '-network', prefix + '-assets'
    containers, images = [], []
    docker('network', 'create', network)
    docker('volume', 'create', volume)
    try:
        with tempfile.TemporaryDirectory(prefix=prefix) as folder:
            root = pathlib.Path(folder)
            upstream = root / 'upstream.py'
            upstream.write_text('''from http.server import BaseHTTPRequestHandler, HTTPServer
import json
class Handler(BaseHTTPRequestHandler):
 def do_GET(self): self.reply()
 def do_POST(self): self.reply()
 def reply(self):
  body=self.rfile.read(int(self.headers.get('Content-Length',0))).decode()
  self.send_response(201 if self.command == 'POST' else 200)
  self.send_header('Content-Type','application/json')
  self.send_header('Set-Cookie','refresh=test-only; HttpOnly; Path=/server/directory')
  self.end_headers()
  self.wfile.write(json.dumps({'path':self.path,'method':self.command,'body':body,'authorization':self.headers.get('Authorization'),'cookie':self.headers.get('Cookie'),'forwardedHost':self.headers.get('X-Forwarded-Host')}).encode())
HTTPServer(('0.0.0.0',8080),Handler).serve_forever()
''')
            backend = prefix + '-backend'
            containers.append(backend)
            docker('run', '-d', '--name', backend, '--network', network, '--network-alias', 'backend', '-v', f'{upstream}:/upstream.py:ro', 'python:3.12-slim', 'python', '/upstream.py')
            for release, hash_value in [('A', 'AAAAAAAA'), ('B', 'BBBBBBBB')]:
                context = root / release
                context.mkdir()
                (context / 'html').mkdir()
                shutil.copy(FRONTEND / 'nginx.conf', context / 'nginx.conf')
                shutil.copy(FRONTEND / 'docker/25-publish-assets.sh', context / 'publish.sh')
                (context / 'html/index.html').write_text(f'''<!doctype html><html><body><h1>Release {release}</h1><button id="lazy">Load older tab module</button><output id="result"></output><script src="/runtime-config.js"></script><script type="module">document.querySelector('#lazy').onclick=async()=>{{try{{const value=await import('/chunk-{hash_value}.js');document.querySelector('#result').textContent=value.release+' / '+window.__NATIART_CONFIG__.release;}}catch(error){{document.querySelector('#result').textContent='FAILED';}}}};</script></body></html>''')
                (context / f'html/chunk-{hash_value}.js').write_text(f'export const release = "{release}";')
                (context / 'html/runtime-config.js').write_text('window.__NATIART_CONFIG__ = {release:"image-default"};')
                (context / 'html/fonts').mkdir()
                (context / 'html/fonts/playfair-display-variable.woff2').write_text(f'font-{release}')
                urlsafe_hash = 'A_A-A_A-' if release == 'A' else 'B_B-B_B_'
                (context / f'html/chunk-{urlsafe_hash}.js').write_text(f'export const release = "{release}";')
                for language in ['en', 'pt-BR']:
                    (context / 'html' / language).mkdir()
                    shutil.copytree(context / 'html/fonts', context / 'html' / language / 'fonts')
                    shutil.copy(context / 'html/index.html', context / 'html' / language / 'index.html')
                    shutil.copy(context / f'html/chunk-{hash_value}.js', context / 'html' / language / f'chunk-{hash_value}.js')
                    shutil.copy(context / f'html/chunk-{urlsafe_hash}.js', context / 'html' / language / f'chunk-{urlsafe_hash}.js')
                (context / 'Dockerfile').write_text('''FROM nginx:1.27-alpine
ENV NATIART_PUBLIC_SCHEME=http
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --chmod=755 publish.sh /docker-entrypoint.d/25-publish-assets.sh
COPY html /usr/share/nginx/html
''')
                image = prefix + ':' + release.lower()
                images.append(image)
                docker('build', '-q', '-t', image, str(context))
                (root / f'config-{release}.js').write_text(f'window.__NATIART_CONFIG__ = {{release:"{release}"}};')

            def launch(release, port=None):
                name = prefix + '-' + release.lower()
                containers.append(name)
                docker('run', '-d', '--name', name, '--network', network, '-p', f'127.0.0.1:{port or ""}:80', '-v', f'{volume}:/var/lib/natiart-assets', '-v', f'{root / ("config-" + release + ".js")}:/run/natiart/runtime-config.js:ro', '-e', 'NATIART_RUNTIME_CONFIG_FILE=/run/natiart/runtime-config.js', '-e', 'DIRECTORY_UPSTREAM=http://backend:8080', '-e', 'PRODUCT_UPSTREAM=http://backend:8080', prefix + ':' + release.lower())
                actual_port = int(docker('port', name, '80/tcp').rsplit(':', 1)[1])
                ready(actual_port)
                return actual_port

            port = launch('A')
            assert 'Release A' in request(port, '/products/deep/link')[2]
            assert request(port, '/chunk-AAAAAAAA.js')[0] == 200
            assert 'immutable' in request(port, '/chunk-AAAAAAAA.js')[1]['Cache-Control']
            for locale_prefix in ['', '/en', '/pt-BR']:
                assert 'immutable' in request(port, f'{locale_prefix}/chunk-A_A-A_A-.js')[1]['Cache-Control'], 'URL-safe hashes must receive immutable caching'
            assert 'no-store' in request(port, '/runtime-config.js')[1]['Cache-Control']
            for font_prefix in ['', '/en', '/pt-BR']:
                assert request(port, f'{font_prefix}/fonts/playfair-display-variable.woff2')[1]['Cache-Control'] == 'no-cache'
                assert request(port, f'{font_prefix}/fonts/playfair-display-variable.woff2')[2] == 'font-A'
            if args.interactive:
                input(f'Release A ready at http://127.0.0.1:{port}/products/deep/link . Open the old tab, then press Enter to replace A: ')
            docker('rm', '-f', prefix + '-a')
            launch('B', port)
            for path in ['/', '/index.html', '/products/deep/link', '/checkout', '/en/checkout', '/pt-BR/checkout']:
                status, headers, body = request(port, path)
                assert status == 200 and 'Release B' in body, path
                assert headers['Cache-Control'] == 'no-store', path
            assert 'release:"B"' in request(port, '/runtime-config.js')[2]
            assert request(port, '/runtime-config.js')[1]['Cache-Control'] == 'no-store'
            for font_prefix in ['', '/en', '/pt-BR']:
                assert request(port, f'{font_prefix}/fonts/playfair-display-variable.woff2')[1]['Cache-Control'] == 'no-cache'
                assert request(port, f'{font_prefix}/fonts/playfair-display-variable.woff2')[2] == 'font-B'
            assert request(port, '/fonts/missing.woff2')[0] == 404
            assert docker('run', '--rm', '--entrypoint', 'find', '-v', f'{volume}:/var/lib/natiart-assets',
                          prefix + ':b', '/var/lib/natiart-assets', '-type', 'f', '-path', '*/fonts/*') == ''
            for language in ['en', 'pt-BR']:
                for path in [f'/{language}/', f'/{language}/dashboard', f'/{language}/dashboard/?preview=1']:
                    status, headers, body = request(port, path)
                    assert status == 200 and 'Release B' in body, path
                    assert headers['Cache-Control'] == 'no-store', path
                    assert f'</{language}/assets/img/a1.webp>; rel=preload; as=image' in headers['Link'], path
                for path in [f'/{language}/login', f'/{language}/checkout', f'/{language}/products']:
                    assert 'Link' not in request(port, path)[1], path
            # A had never requested its lazy chunk before the switch. B still serves it.
            assert '"A"' in request(port, '/chunk-AAAAAAAA.js')[2]
            assert '"B"' in request(port, '/chunk-BBBBBBBB.js')[2]
            for language in ['en', 'pt-BR']:
                assert '"A"' in request(port, f'/{language}/chunk-AAAAAAAA.js')[2]
                assert 'immutable' in request(port, f'/{language}/chunk-AAAAAAAA.js')[1]['Cache-Control']
                assert '"B"' in request(port, f'/{language}/chunk-BBBBBBBB.js')[2]
            for locale_prefix in ['', '/en', '/pt-BR']:
                for hash_value, release in [('A_A-A_A-', 'A'), ('B_B-B_B_', 'B')]:
                    status, headers, body = request(port, f'{locale_prefix}/chunk-{hash_value}.js')
                    assert status == 200 and f'"{release}"' in body, 'URL-safe old chunks must survive replacement'
                    assert 'immutable' in headers['Cache-Control']
            assert request(port, '/chunk-MISSING0.js')[0] == 404
            assert request(port, '/missing.js')[0] == 404
            assert request(port, '/server/unknown')[0] == 404
            for service in ['directory', 'product']:
                status, headers, body = request(port, f'/server/{service}/echo?next=%2Fcart', data=b'{"test":true}', headers={'Content-Type': 'application/json', 'Authorization': 'Bearer test-only', 'Cookie': 'refresh=test-only'})
                response = json.loads(body)
                assert status == 201 and response['path'] == '/echo?next=%2Fcart'
                assert response['body'] == '{"test":true}' and response['method'] == 'POST'
                assert response['authorization'] == 'Bearer test-only' and response['cookie'] == 'refresh=test-only'
                assert 'HttpOnly' in headers['Set-Cookie'] and response['forwardedHost'] == '127.0.0.1'
            # Exercise the actual multipart creation route above nginx's default 1 MiB.
            boundary = 'natiart-upload-boundary'
            body = (f'--{boundary}\r\nContent-Disposition: form-data; name="productDto"\r\nContent-Type: application/json\r\n\r\n{{"label":"fixture"}}\r\n'
                    f'--{boundary}\r\nContent-Disposition: form-data; name="newImages"; filename="fixture.webp"\r\nContent-Type: image/webp\r\n\r\n').encode() + b'a' * (2 * 1024 * 1024) + f'\r\n--{boundary}--\r\n'.encode()
            status, headers, received = request(port, '/server/product/products/create?fixture=upload', data=body,
                headers={'Content-Type': f'multipart/form-data; boundary={boundary}', 'Authorization': 'Bearer upload-fixture', 'Cookie': 'fixture=upload'})
            received = json.loads(received)
            assert status == 201 and received['path'] == '/products/create?fixture=upload'
            assert received['body'].encode() == body
            assert received['authorization'] == 'Bearer upload-fixture' and received['cookie'] == 'fixture=upload'
            # nginx rejects an oversized declared body before accepting its bytes.
            for path, length in [('/server/product/products/create', 102 * 1024 * 1024), ('/server/directory/register-user', 2 * 1024 * 1024)]:
                connection = http.client.HTTPConnection('127.0.0.1', port, timeout=5)
                connection.putrequest('POST', path)
                connection.putheader('Content-Type', f'multipart/form-data; boundary={boundary}')
                connection.putheader('Content-Length', str(length))
                connection.endheaders()
                assert connection.getresponse().status == 413, path
                connection.close()
            # Rollback uses its image shell/config while retaining B's chunks too.
            rollback = prefix + '-rollback'
            containers.append(rollback)
            docker('run', '-d', '--name', rollback, '--network', network, '-p', '127.0.0.1::80', '-v', f'{volume}:/var/lib/natiart-assets', '-e', 'DIRECTORY_UPSTREAM=http://backend:8080', '-e', 'PRODUCT_UPSTREAM=http://backend:8080', prefix + ':a')
            rollback_port = int(docker('port', rollback, '80/tcp').rsplit(':', 1)[1])
            ready(rollback_port)
            assert 'Release A' in request(rollback_port, '/products/rollback')[2]
            assert request(rollback_port, '/chunk-BBBBBBBB.js')[0] == 200
            for locale_prefix in ['', '/en', '/pt-BR']:
                assert request(rollback_port, f'{locale_prefix}/chunk-B_B-B_B_.js')[0] == 200, 'URL-safe new chunks must survive rollback'
            assert request(rollback_port, '/runtime-config.js')[1]['Cache-Control'] == 'no-store'
            for font_prefix in ['', '/en', '/pt-BR']:
                assert request(rollback_port, f'{font_prefix}/fonts/playfair-display-variable.woff2')[1]['Cache-Control'] == 'no-cache'
                assert request(rollback_port, f'{font_prefix}/fonts/playfair-display-variable.woff2')[2] == 'font-A'
            if args.interactive:
                input('Release B is ready. Click the old-tab button and check A / A, then open a new tab and check B / B. Press Enter to clean up: ')
            for suffix, extra_env, expected in [
                ('missing-upstream', [], 'DIRECTORY_UPSTREAM'),
                ('invalid-upstream', ['-e', 'DIRECTORY_UPSTREAM=http://backend:8080/path', '-e', 'PRODUCT_UPSTREAM=http://backend:8080'], 'without a path'),
                ('missing-config', ['-e', 'DIRECTORY_UPSTREAM=http://backend:8080', '-e', 'PRODUCT_UPSTREAM=http://backend:8080', '-e', 'NATIART_RUNTIME_CONFIG_FILE=/missing.js'], None),
            ]:
                name = prefix + '-' + suffix
                containers.append(name)
                docker('run', '-d', '--name', name, '--network', network, *extra_env, prefix + ':a')
                assert docker('wait', name) != '0', suffix
                if expected:
                    assert expected in docker('logs', name), suffix
            docker('run', '--rm', '--entrypoint', 'sh', '-v', f'{volume}:/var/lib/natiart-assets', prefix + ':a', '-c', 'printf collision > /var/lib/natiart-assets/chunk-AAAAAAAA.js')
            name = prefix + '-collision'
            containers.append(name)
            docker('run', '-d', '--name', name, '--network', network, '-v', f'{volume}:/var/lib/natiart-assets', '-e', 'DIRECTORY_UPSTREAM=http://backend:8080', '-e', 'PRODUCT_UPSTREAM=http://backend:8080', prefix + ':a')
            assert docker('wait', name) != '0'
            assert 'Immutable asset collision' in docker('logs', name)
            print('PASS: A-to-B replacement, deep links, runtime config/cache, retained lazy assets, both API proxies, rollback and startup failures')
    finally:
        for name in reversed(containers):
            subprocess.run(['docker', 'rm', '-f', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for image in images:
            subprocess.run(['docker', 'image', 'rm', image], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        docker('volume', 'rm', volume)
        docker('network', 'rm', network)


if __name__ == '__main__':
    main()
