#!/usr/bin/env python3
"""Exercise disposable local accounts with real authenticated reads; never contacts a remote shop."""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:4300')
    parser.add_argument('--accounts', type=int, default=20)
    parser.add_argument('--requests', type=int, default=1000)
    parser.add_argument('--concurrency', type=int, default=25)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    parsed = urllib.parse.urlparse(args.base_url)
    if parsed.scheme != 'http' or parsed.hostname not in {'localhost', '127.0.0.1', '::1'} or parsed.username or parsed.password:
        parser.error('Use a loopback-only HTTP preview with disposable data and local provider fixtures.')
    if not (1 <= args.accounts <= 100 and 1 <= args.requests <= 10000 and 1 <= args.concurrency <= 100):
        parser.error('Bounds: 1–100 accounts, 1–10000 reads, 1–100 workers.')
    base = args.base_url.rstrip('/')

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, request, response, code, message, headers, new_url):
            return None

    opener = urllib.request.build_opener(NoRedirect())

    def request(path, token=None, body=None):
        headers = {'Content-Type': 'application/json'}
        if token: headers['Authorization'] = 'Bearer ' + token
        data = json.dumps(body).encode() if body is not None else None
        with opener.open(urllib.request.Request(base + path, data=data, headers=headers), timeout=30) as response:
            return json.loads(response.read())

    sessions = []
    run_id = uuid.uuid4().hex
    for index in range(args.accounts):
        email = f'smoke-{run_id}-{index}@example.test'
        password = 'LocalSmoke123-' + secrets.token_urlsafe(18)
        account = request('/server/directory/register-user', body={
            'username': email, 'password': password, 'profile': {
                'firstname': 'Local', 'lastname': 'Smoke', 'cpf': '12345678909', 'phone': '', 'country': 'Brazil',
                'state': 'SP', 'city': 'São Paulo', 'neighborhood': 'Centro', 'zipCode': '01001000',
                'street': 'Rua de Teste', 'houseNumber': '123', 'complement': 'Apartamento 4'}})
        login = request('/server/directory/login', body={'username': email, 'password': password})
        sessions.append((account['id'], login['accessToken']))
    paths = ['/server/directory/users/current', '/server/product/orders', '/server/product/products/page?size=12',
             '/server/directory/users/current', '/server/product/orders']

    def read(index):
        path = paths[index % len(paths)]
        identity, token = sessions[(index // len(paths)) % len(sessions)]
        start = time.perf_counter()
        try:
            payload = request(path, token)
            if path.endswith('/users/current'):
                assert payload['id'] == identity and payload['profile']['houseNumber'] == '123'
            elif path.endswith('/orders'):
                assert payload == [], 'An unrelated order must never appear in a fresh account'
            else:
                assert isinstance(payload['items'], list)
            outcome = '200'
        except urllib.error.HTTPError as error:
            outcome = str(error.code)
        except Exception as error:
            outcome = type(error).__name__
        return path, outcome, (time.perf_counter() - start) * 1000

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        results = list(executor.map(read, range(args.requests)))
    seconds = time.perf_counter() - started
    latencies = sorted(value[2] for value in results)
    outcomes = Counter(value[1] for value in results)
    metrics = {
        'scope': 'Loopback Nginx + Java 25 + local H2 + synthetic provider; read-only measured phase',
        'accounts': args.accounts, 'requests': args.requests, 'concurrency': args.concurrency,
        'outcomes': dict(outcomes), 'seconds': round(seconds, 3), 'requestsPerSecond': round(args.requests / seconds, 2),
        'p50Milliseconds': round(latencies[int((len(latencies)-1)*.50)], 2),
        'p95Milliseconds': round(latencies[int((len(latencies)-1)*.95)], 2),
        'p99Milliseconds': round(latencies[int((len(latencies)-1)*.99)], 2),
        'maxMilliseconds': round(latencies[-1], 2),
        'routes': dict(Counter(value[0] for value in results)),
        'limitations': 'Excludes browser rendering, registration/login timings, payments and real providers. Uses elevated local authentication limits. Does not certify 1000 production users.'}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(metrics, indent=2) + '\n')
    print(json.dumps(metrics, indent=2))
    if outcomes != Counter({'200': args.requests}): raise SystemExit(1)


if __name__ == '__main__':
    main()
