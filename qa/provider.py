"""Private, disposable QA doubles for Asaas, shipping and recovery delivery."""
import base64
import datetime as dt
import hashlib
import hmac
import html
import json
import os
from pathlib import Path
import secrets
import sqlite3
import time
from contextlib import contextmanager
from decimal import Decimal, InvalidOperation
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit
import urllib.request

ROOT = Path(__file__).resolve().parent
STATE = Path(os.environ.get('QA_STATE_DIR', '/state'))
API_KEY = os.environ.get('NATIART_PAYMENT_ASAAS_APIKEY', 'qa-local-provider-key')
CONTROL_TOKEN = os.environ.get('NATIART_QA_CONTROL_TOKEN', 'qa-control-change-me')
WEBHOOK_TOKEN = os.environ.get('NATIART_PAYMENT_ASAAS_WEBHOOK_TOKEN', 'qa-local-webhook-token')
PRODUCT_URL = os.environ.get('PRODUCT_SERVICE_URL', 'http://natiart-product:8082')
PUBLIC_URL = os.environ.get('QA_PUBLIC_URL', 'http://localhost:4401').rstrip('/')
MAX_BODY = 65536


@contextmanager
def connect():
    db = sqlite3.connect(STATE / 'providers.sqlite3', timeout=20)
    db.row_factory = sqlite3.Row
    try:
        with db:
            yield db
    finally:
        db.close()


def initialize():
    STATE.mkdir(parents=True, exist_ok=True)
    with connect() as db:
        db.executescript('''
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS customer (id TEXT PRIMARY KEY, external_reference TEXT UNIQUE, data TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS payment (id TEXT PRIMARY KEY, request_key TEXT UNIQUE, fingerprint TEXT, data TEXT NOT NULL, expires_at TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS notification (id INTEGER PRIMARY KEY, recipient TEXT NOT NULL, reset_link TEXT NOT NULL, created_at TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS order_notification (id TEXT PRIMARY KEY, recipient TEXT NOT NULL, subject TEXT NOT NULL, body TEXT NOT NULL, created_at TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS session (token_hash TEXT PRIMARY KEY, csrf TEXT NOT NULL, expires_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS login_attempt (peer TEXT PRIMARY KEY, window INTEGER NOT NULL, attempts INTEGER NOT NULL);
        ''')
        reset_customers(db)
        reset_payments(db)
        db.execute('DELETE FROM session')
        db.execute('DELETE FROM login_attempt')


def reset_customers(db):
    db.execute('DELETE FROM customer')
    db.execute('DELETE FROM notification')
    for data in json.loads((ROOT / 'customers.json').read_text()):
        data = customer_response(data)
        db.execute('INSERT INTO customer VALUES (?, ?, ?)', (data['id'], data['externalReference'], json.dumps(data)))


def customer_response(data):
    # The real customer DTO requires its primitive fields even when optional text is absent.
    return dict(object='customer', dateCreated=utc_now().date().isoformat(), personType='FISICA',
                canDelete=True, canEdit=True, city=0, notificationDisabled=False) | data


def reset_payments(db):
    db.execute('DELETE FROM payment')
    db.execute('DELETE FROM order_notification')
    for payment in json.loads((ROOT / 'history-payments.json').read_text()):
        db.execute('INSERT INTO payment VALUES (?, NULL, NULL, ?, ?)',
                   (payment['id'], json.dumps(payment), (utc_now() + dt.timedelta(hours=1)).isoformat()))


def utc_now():
    return dt.datetime.now(dt.timezone.utc)


def create_payment(db, body, key):
    value = Decimal(str(body['value']))
    if not value.is_finite() or value <= 0 or value.as_tuple().exponent < -2:
        raise ValueError('Invalid amount')
    if not db.execute('SELECT 1 FROM customer WHERE id=?', (body['customer'],)).fetchone():
        raise ValueError('Unknown QA customer')
    fingerprint = hashlib.sha256(json.dumps(body, sort_keys=True).encode()).hexdigest()
    db.execute('BEGIN IMMEDIATE')
    existing = db.execute('SELECT data, fingerprint FROM payment WHERE request_key=?', (key,)).fetchone()
    if existing:
        if existing['fingerprint'] != fingerprint:
            raise ValueError('Idempotency key was reused with different data')
        return json.loads(existing['data'])
    today = utc_now().date()
    pid = 'pay_qa_' + secrets.token_hex(12)
    payment = dict(id=pid, dateCreated=today.isoformat(), customer=body['customer'], billingType='PIX',
                   value=float(value), status='PENDING', dueDate=body.get('dueDate', today.isoformat()),
                   invoiceUrl=PUBLIC_URL + '/en/account', invoiceNumber=pid, deleted=False, currency='BRL')
    expires = (utc_now() + dt.timedelta(hours=1)).isoformat()
    db.execute('INSERT INTO payment VALUES (?, ?, ?, ?, ?)', (pid, key, fingerprint, json.dumps(payment), expires))
    return payment


def transition_payment(pid, status):
    with connect() as db:
        db.execute('BEGIN IMMEDIATE')
        row = db.execute('SELECT data FROM payment WHERE id=?', (pid,)).fetchone()
        if not row:
            raise ValueError('Unknown QA payment')
        payment = json.loads(row['data'])
        if payment['status'] not in ('PENDING', 'OVERDUE', status):
            raise ValueError('A completed payment cannot change through this QA action')
        payment['status'] = status
        db.execute('UPDATE payment SET data=? WHERE id=?', (json.dumps(payment), pid))
    event = 'PAYMENT_CONFIRMED' if status == 'CONFIRMED' else 'PAYMENT_OVERDUE'
    payload = dict(id='evt_qa_' + status.lower() + '_' + pid, event=event, payment=payment)
    request = urllib.request.Request(PRODUCT_URL + '/webhooks/asaas', data=json.dumps(payload).encode(),
                                     headers={'Content-Type': 'application/json', 'asaas-access-token': WEBHOOK_TOKEN}, method='POST')
    with urllib.request.urlopen(request, timeout=15) as response:
        response.read()
    return payment


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        # Recovery links, sessions and provider keys must never enter access logs.
        pass

    def respond(self, status, data, content_type='application/json', headers=None):
        body = json.dumps(data).encode() if content_type == 'application/json' else data.encode()
        self.send_response(status)
        for name, value in (headers or {}).items():
            self.send_header(name, value)
        self.send_header('Content-Type', content_type + '; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def redirect(self, cookie=None):
        headers = {'Location': '/qa/'}
        if cookie:
            headers['Set-Cookie'] = cookie
        self.respond(303, '', 'text/plain', headers)

    def read_body(self):
        length = self.headers.get('Content-Length')
        if self.headers.get('Transfer-Encoding', '').lower() == 'chunked':
            if length is not None:
                raise ValueError('Ambiguous body framing')
            chunks = bytearray()
            while True:
                line = self.rfile.readline(128)
                size = int(line.split(b';', 1)[0].strip(), 16)
                if size < 0 or len(chunks) + size > MAX_BODY:
                    raise ValueError('Body too large')
                if size == 0:
                    if self.rfile.readline(128) != b'\r\n':
                        raise ValueError('Trailers are not supported')
                    break
                chunk = self.rfile.read(size)
                if len(chunk) != size or self.rfile.read(2) != b'\r\n':
                    raise ValueError('Invalid chunk')
                chunks.extend(chunk)
            return bytes(chunks)
        size = int(length or '0')
        if size < 0 or size > MAX_BODY:
            raise ValueError('Body too large')
        return self.rfile.read(size)

    def control_session(self):
        cookies = dict(part.strip().split('=', 1) for part in self.headers.get('Cookie', '').split(';') if '=' in part)
        token = cookies.get('natiart_qa_session', '')
        with connect() as db:
            db.execute('DELETE FROM session WHERE expires_at < ?', (int(time.time()),))
            return db.execute('SELECT csrf FROM session WHERE token_hash=?', (hashlib.sha256(token.encode()).hexdigest(),)).fetchone()

    def is_control(self, form=None):
        supplied = self.headers.get('X-NatiArt-QA-Token', '')
        if supplied and hmac.compare_digest(supplied, CONTROL_TOKEN):
            return True
        session = self.control_session()
        return bool(form is not None and session and hmac.compare_digest(form.get('csrf', [''])[0], session['csrf']))

    def is_provider(self):
        supplied = self.headers.get('access_token', '')
        if urlsplit(self.path).path == '/shipping':
            supplied = self.headers.get('Authorization', '').removeprefix('Bearer ')
        return bool(supplied and hmac.compare_digest(supplied, API_KEY))

    def page(self):
        session = self.control_session()
        content = '<form method="post" action="/qa/login"><label>QA control password<input type="password" name="password" required autocomplete="current-password"></label><button>Open QA controls</button></form>'
        if session:
            csrf = html.escape(session['csrf'], quote=True)
            with connect() as db:
                payments = [json.loads(row['data']) for row in db.execute('SELECT data FROM payment ORDER BY rowid DESC')]
                messages = db.execute('SELECT * FROM notification ORDER BY id DESC LIMIT 100').fetchall()
                order_messages = db.execute('SELECT * FROM order_notification ORDER BY created_at DESC LIMIT 100').fetchall()
            rows = []
            for payment in payments:
                pid = html.escape(payment['id'], quote=True)
                status = html.escape(payment['status'])
                actions = ''
                if payment['status'] in ('PENDING', 'OVERDUE'):
                    for action, label in [('confirm', 'Confirm test payment'), ('expire', 'Mark overdue')]:
                        actions += f'<form method="post" action="/qa/{action}/{pid}"><input type="hidden" name="csrf" value="{csrf}"><button>{label}</button></form>'
                rows.append(f'<tr><td>{pid}</td><td>R$ {Decimal(str(payment["value"])):.2f}</td><td>{status}</td><td>{actions}</td></tr>')
            inbox = ''.join(f'<li><strong>{html.escape(row["recipient"])}</strong><br><a href="{html.escape(row["reset_link"], quote=True)}">Open password reset</a><br><small>{html.escape(row["created_at"])}</small></li>' for row in messages)
            order_inbox = ''.join(f'<li><strong>{html.escape(row["subject"])}</strong> - {html.escape(row["recipient"])}<pre style="white-space:pre-wrap;overflow-wrap:anywhere">{html.escape(row["body"])}</pre></li>' for row in order_messages)
            content = f'<h2>Purchase updates</h2><ul>{order_inbox or "<li>No purchase updates yet. Place or progress an order.</li>"}</ul><h2>Test PIX payments</h2><div class="table"><table><thead><tr><th>Payment</th><th>Amount</th><th>Status</th><th>Actions</th></tr></thead><tbody>{"".join(rows)}</tbody></table></div><h2>Recovery inbox</h2><ul>{inbox or "<li>No reset messages yet. Request one from the storefront.</li>"}</ul><form method="post" action="/qa/logout"><input type="hidden" name="csrf" value="{csrf}"><button>Sign out of QA controls</button></form>'
        page = '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>NatiArt QA controls</title>
        <style>body{font:16px/1.6 system-ui;margin:0;background:#faf7f1;color:#352e2b}main{max-width:70rem;margin:auto;padding:2rem 1rem}h1,h2{font-family:Georgia,serif;font-weight:400}h1{font-size:2.5rem}.notice{border-left:3px solid #805045;padding:1rem;background:#f2e6df}a{color:#73463b}button,input{font:inherit;min-height:44px;box-sizing:border-box;padding:.5rem .75rem}button{color:#fff;background:#73463b;border:0;border-radius:3px;cursor:pointer}button:focus-visible,input:focus-visible,a:focus-visible{outline:3px solid #352e2b;outline-offset:3px}label{display:grid;gap:.5rem}form{margin:.75rem 0}.table{overflow:auto}table{width:100%;border-collapse:collapse;font-size:.875rem}td,th{text-align:left;padding:.75rem;border-bottom:1px solid #d8c8bd}td:first-child{overflow-wrap:anywhere}ul{padding-left:1.5rem}li{margin:1rem 0}</style>
        <main><p>NATIART / QA</p><h1>Test the whole journey.</h1><p class="notice">Fictional catalog, simulated shipping, and test-only PIX. No real money or email is sent.</p><p><a href="/en/dashboard">English storefront</a> · <a href="/pt-BR/dashboard">Loja em português</a></p>CONTENT</main></html>'''.replace('CONTENT', content)
        self.respond(200, page, 'text/html')

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == '/healthz':
            with connect() as db:
                db.execute('SELECT 1').fetchone()
            return self.respond(200, {'ready': True})
        if path in ('/review', '/review/'):
            return self.page()
        if path.startswith('/review/'):
            if not self.is_control():
                return self.respond(403, {'error': 'QA control token required'})
            with connect() as db:
                if path == '/review/payments':
                    return self.respond(200, [json.loads(row['data']) for row in db.execute('SELECT data FROM payment')])
                if path == '/review/order-notifications':
                    return self.respond(200, [dict(row) for row in db.execute('SELECT * FROM order_notification ORDER BY created_at DESC LIMIT 100')])
                if path == '/review/notifications':
                    return self.respond(200, [dict(row) for row in db.execute('SELECT * FROM notification ORDER BY id DESC LIMIT 100')])
            return self.respond(404, {'error': 'Unknown QA endpoint'})
        if not self.is_provider():
            return self.respond(401, {'error': 'QA provider key required'})
        with connect() as db:
            if path == '/customers':
                reference = parse_qs(urlsplit(self.path).query).get('externalReference', [''])[0]
                rows = db.execute('SELECT data FROM customer WHERE external_reference=?', (reference,)).fetchall()
                return self.respond(200, dict(data=[json.loads(row['data']) for row in rows], hasMore=False, totalCount=len(rows)))
            if path.startswith('/payments/'):
                pid = path.split('/')[2]
                row = db.execute('SELECT data, expires_at FROM payment WHERE id=?', (pid,)).fetchone()
                if row:
                    if path.endswith('/pixQrCode'):
                        expiry = dt.datetime.fromisoformat(row['expires_at']).strftime('%Y-%m-%d %H:%M:%S')
                        return self.respond(200, dict(success=True, encodedImage=base64.b64encode((ROOT / 'assets/demo-qr.png').read_bytes()).decode(), payload='NATIART-LOCAL-REVIEW-NOT-A-PAYMENT', expirationDate=expiry))
                    return self.respond(200, json.loads(row['data']))
        self.respond(404, {'error': 'Unknown QA resource'})

    def do_POST(self):
        try:
            self.post()
        except (ValueError, KeyError, TypeError, InvalidOperation):
            self.respond(400, {'error': 'Invalid QA request'})
        except (OSError, sqlite3.Error):
            self.respond(503, {'error': 'QA provider unavailable; retry or allow payment reconciliation'})

    def post(self):
        path = urlsplit(self.path).path
        raw = self.read_body()
        form = parse_qs(raw.decode()) if self.headers.get('Content-Type', '').startswith('application/x-www-form-urlencoded') else None
        if path == '/review/login':
            password = (form or {}).get('password', [''])[0]
            with connect() as db:
                peer = self.client_address[0]
                window = int(time.time()) // 60
                db.execute('INSERT INTO login_attempt VALUES (?, ?, 1) ON CONFLICT(peer) DO UPDATE SET attempts=CASE WHEN window=excluded.window THEN attempts+1 ELSE 1 END, window=excluded.window', (peer, window))
                if db.execute('SELECT attempts FROM login_attempt WHERE peer=?', (peer,)).fetchone()['attempts'] > 10:
                    return self.respond(429, {'error': 'Try QA login later'})
                if not hmac.compare_digest(password, CONTROL_TOKEN):
                    return self.respond(403, {'error': 'Incorrect QA control password'})
                token = secrets.token_urlsafe(32)
                db.execute('INSERT INTO session VALUES (?, ?, ?)', (hashlib.sha256(token.encode()).hexdigest(), secrets.token_urlsafe(24), int(time.time()) + 3600))
            secure = '; Secure' if urlsplit(PUBLIC_URL).scheme == 'https' else ''
            return self.redirect('natiart_qa_session=' + token + '; HttpOnly; SameSite=Strict; Path=/qa/' + secure)
        if path.startswith('/review/'):
            if not self.is_control(form):
                return self.respond(403, {'error': 'QA control authorization required'})
            if path == '/review/logout':
                cookies = dict(part.strip().split('=', 1) for part in self.headers.get('Cookie', '').split(';') if '=' in part)
                with connect() as db:
                    db.execute('DELETE FROM session WHERE token_hash=?',
                               (hashlib.sha256(cookies.get('natiart_qa_session', '').encode()).hexdigest(),))
                return self.redirect('natiart_qa_session=; Max-Age=0; HttpOnly; SameSite=Strict; Path=/qa/')
            parts = path.split('/')
            if len(parts) == 4 and parts[2] in ('confirm', 'expire'):
                payment = transition_payment(parts[3], 'CONFIRMED' if parts[2] == 'confirm' else 'OVERDUE')
                return self.redirect() if form is not None else self.respond(200, payment)
            return self.respond(404, {'error': 'Unknown QA action'})
        body = json.loads(raw or b'{}')
        if path == '/notifications/orders':
            if not self.is_control():
                return self.respond(403, {'error': 'QA control token required'})
            if not all(isinstance(body.get(key), str) and body[key] for key in ('id', 'recipient', 'subject', 'body')) or len(body['body']) > 16000:
                raise ValueError('Invalid order update')
            with connect() as db:
                db.execute('INSERT OR IGNORE INTO order_notification VALUES (?, ?, ?, ?, ?)',
                           (body['id'], body['recipient'], body['subject'], body['body'], utc_now().isoformat()))
                db.execute('DELETE FROM order_notification WHERE id NOT IN (SELECT id FROM order_notification ORDER BY created_at DESC LIMIT 100)')
            return self.respond(201, {'accepted': True})
        if path == '/notifications/password-reset':
            if not self.is_control():
                return self.respond(403, {'error': 'QA control token required'})
            link = urlsplit(body['resetLink'])
            public = urlsplit(PUBLIC_URL)
            if (link.scheme, link.netloc) != (public.scheme, public.netloc) or not link.fragment or len(body['resetLink']) > 4096:
                raise ValueError('Unexpected QA reset link')
            with connect() as db:
                db.execute('INSERT INTO notification (recipient, reset_link, created_at) VALUES (?, ?, ?)', (body['recipient'], body['resetLink'], utc_now().isoformat()))
                db.execute('DELETE FROM notification WHERE id NOT IN (SELECT id FROM notification ORDER BY id DESC LIMIT 100)')
            return self.respond(201, {'accepted': True})
        if not self.is_provider():
            return self.respond(401, {'error': 'QA provider key required'})
        if path in ('/internal/reset-customers', '/internal/reset-payments'):
            with connect() as db:
                (reset_customers if path.endswith('customers') else reset_payments)(db)
            return self.respond(200, {'reset': True})
        if path == '/shipping':
            return self.respond(200, [dict(id=i, name=name, price=price, delivery_time=days, company=dict(id=1, name='Correios')) for i, name, price, days in [(1, 'PAC', '18.90', 7), (2, 'SEDEX', '29.90', 3)]])
        with connect() as db:
            if path == '/customers':
                reference = body['externalReference']
                existing = db.execute('SELECT data FROM customer WHERE external_reference=?', (reference,)).fetchone()
                if existing:
                    return self.respond(200, json.loads(existing['data']))
                data = customer_response(dict(body, id='cus_qa_' + secrets.token_hex(12), deleted=False))
                db.execute('INSERT INTO customer VALUES (?, ?, ?)', (data['id'], reference, json.dumps(data)))
                db.commit()
                return self.respond(200, data)
            if path == '/payments':
                key = self.headers.get('Idempotency-Key', '')
                if not key or len(key) > 128:
                    raise ValueError('Missing payment key')
                payment = create_payment(db, body, key)
                # A backend may immediately replay/read after the response body arrives.
                db.commit()
                return self.respond(200, payment)
        self.respond(404, {'error': 'Unknown QA endpoint'})

    def do_PUT(self):
        try:
            if not self.is_provider():
                return self.respond(401, {'error': 'QA provider key required'})
            path = urlsplit(self.path).path
            body = json.loads(self.read_body())
            if not path.startswith('/customers/'):
                return self.respond(404, {'error': 'Unknown QA resource'})
            cid = path.rsplit('/', 1)[-1]
            with connect() as db:
                row = db.execute('SELECT data FROM customer WHERE id=?', (cid,)).fetchone()
                if not row:
                    return self.respond(404, {'error': 'Unknown QA customer'})
                data = dict(json.loads(row['data']), **body)
                data.update(id=cid, deleted=False)
                db.execute('UPDATE customer SET data=?, external_reference=? WHERE id=?', (json.dumps(data), data['externalReference'], cid))
                db.commit()
                self.respond(200, data)
        except (ValueError, KeyError, TypeError):
            self.respond(400, {'error': 'Invalid QA customer update'})

    def do_DELETE(self):
        if not self.is_provider():
            return self.respond(401, {'error': 'QA provider key required'})
        path = urlsplit(self.path).path
        if not path.startswith('/payments/') or len(path.split('/')) != 3:
            return self.respond(404, {'error': 'Unknown QA resource'})
        with connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute('SELECT data FROM payment WHERE id=?', (path.rsplit('/', 1)[-1],)).fetchone()
            if not row:
                return self.respond(404, {'error': 'Unknown QA payment'})
            payment = json.loads(row['data'])
            if payment['status'] not in ('PENDING', 'OVERDUE', 'CANCELLED'):
                return self.respond(409, {'error': 'A paid QA charge cannot be deleted'})
            payment.update(deleted=True, status='CANCELLED')
            db.execute('UPDATE payment SET data=? WHERE id=?', (json.dumps(payment), payment['id']))
            db.commit()
        return self.respond(200, payment)


if __name__ == '__main__':
    initialize()
    print('Private QA providers ready on port 8090.', flush=True)
    ThreadingHTTPServer(('0.0.0.0', 8090), Handler).serve_forever()
