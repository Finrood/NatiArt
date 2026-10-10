"""Exercise the private HTTP provider and the committed gallery contract."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import provider
import prepare_images


class ProviderTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.state = patch.object(provider, 'STATE', Path(self.temp.name))
        self.state.start()
        provider.initialize()
        self.server = provider.ThreadingHTTPServer(('127.0.0.1', 0), provider.Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.origin = f'http://127.0.0.1:{self.server.server_port}'

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.state.stop()
        self.temp.cleanup()

    def request(self, path, data=None, headers=None, expected=200, method=None):
        raw = json.dumps(data).encode() if data is not None else None
        request = urllib.request.Request(self.origin + path, data=raw, headers={'Content-Type': 'application/json', **(headers or {})}, method=method)
        try:
            response = urllib.request.urlopen(request, timeout=5)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            self.assertEqual(expected, response.status)
            return json.loads(response.read())

    def test_payment_replay_conflict_invalid_amount_and_reset(self):
        headers = {'access_token': provider.API_KEY, 'Idempotency-Key': 'test-key'}
        body = {'customer': 'cus_000006360414', 'value': 42.25, 'billingType': 'PIX'}
        payment = self.request('/payments', body, headers)
        self.assertEqual(payment, self.request('/payments', body, headers))
        self.request('/payments', dict(body, value=43), headers, 400)
        self.request('/payments', dict(body, value='garbage'), headers, 400)
        self.request('/payments', dict(body, value='NaN'), headers, 400)
        self.request('/payments', body, {}, 401)
        self.request('/internal/reset-payments', {}, {'access_token': provider.API_KEY})
        self.request('/payments/' + payment['id'], headers=headers, expected=404)
        with provider.connect() as db:
            self.assertEqual(len(json.loads((provider.ROOT / 'history-payments.json').read_text())), db.execute('SELECT COUNT(*) FROM payment').fetchone()[0])

    def test_mutation_is_committed_before_response_is_sent(self):
        original = provider.Handler.respond
        checks = []

        def respond(handler, status, data, *args, **kwargs):
            if status == 200 and handler.path in ('/payments', '/customers') and 'id' in data:
                table = 'payment' if handler.path == '/payments' else 'customer'
                with provider.connect() as reader:
                    checks.append(reader.execute(f'SELECT id FROM {table} WHERE id=?', (data['id'],)).fetchone() is not None)
            return original(handler, status, data, *args, **kwargs)

        with patch.object(provider.Handler, 'respond', respond):
            headers = {'access_token': provider.API_KEY, 'Idempotency-Key': 'committed'}
            self.request('/customers', {'externalReference': 'committed-customer'}, headers)
            self.request('/payments', {'customer': 'cus_000006360414', 'value': 42}, headers)
        self.assertEqual([True, True], checks)

    def test_restart_restores_customers_payments_inbox_and_sessions(self):
        headers = {'access_token': provider.API_KEY}
        self.request('/customers', {'externalReference': 'new-user', 'name': 'QA'}, headers)
        self.request('/payments', {'customer': 'cus_000006360414', 'value': 9}, dict(headers, **{'Idempotency-Key': 'new'}))
        self.request('/notifications/password-reset', {'recipient': 'qa@example.invalid', 'resetLink': provider.PUBLIC_URL + '/en/reset-password#token=test'}, {'X-NatiArt-QA-Token': provider.CONTROL_TOKEN}, 201)
        with provider.connect() as db:
            db.execute('INSERT INTO session VALUES (?, ?, ?)', ('hash', 'csrf', 9999999999))
        provider.initialize()
        self.assertEqual([], self.request('/customers?externalReference=new-user', headers=headers)['data'])
        self.assertEqual([], self.request('/review/notifications', headers={'X-NatiArt-QA-Token': provider.CONTROL_TOKEN}))
        with provider.connect() as db:
            self.assertEqual(0, db.execute('SELECT COUNT(*) FROM session').fetchone()[0])

    def test_internal_resets_and_inbox_require_auth(self):
        for path in ['/internal/reset-customers', '/internal/reset-payments']:
            self.request(path, {}, expected=401)
        self.request('/notifications/password-reset', {}, expected=403)
        self.request('/review/notifications', expected=403)
        self.request('/notifications/password-reset', {'recipient': 'qa@example.invalid', 'resetLink': 'https://foreign.invalid/#token'}, {'X-NatiArt-QA-Token': provider.CONTROL_TOKEN}, 400)

    def test_order_inbox_is_private_idempotent_and_resets_with_product_data(self):
        message = {'id': 'order:PAID', 'recipient': 'buyer@example.test', 'subject': 'Payment confirmed', 'body': 'A purchase update'}
        self.request('/notifications/orders', message, expected=403)
        self.request('/review/order-notifications', expected=403)
        headers = {'X-NatiArt-QA-Token': provider.CONTROL_TOKEN}
        self.request('/notifications/orders', message, headers, 201)
        self.request('/notifications/orders', message, headers, 201)
        self.assertEqual(1, len(self.request('/review/order-notifications', headers=headers)))
        self.request('/internal/reset-payments', {}, {'access_token': provider.API_KEY})
        self.assertEqual([], self.request('/review/order-notifications', headers=headers))

    def test_customer_wire_shape_includes_required_primitives_and_stable_reference(self):
        headers = {'access_token': provider.API_KEY}
        customer = self.request('/customers', {'externalReference': 'wire-shape', 'notificationDisabled': False}, headers)
        for field in ['deleted', 'notificationDisabled', 'canDelete', 'canEdit']:
            self.assertIsInstance(customer[field], bool)
        self.assertIsInstance(customer['city'], int)
        self.assertEqual([customer], self.request('/customers?externalReference=wire-shape', headers=headers)['data'])
        seeded = self.request('/customers?externalReference=789e4567-e89b-12d3-a456-426614174000', headers=headers)['data'][0]
        self.assertEqual(0, seeded['city'])

    def test_charge_deletion_is_idempotent_and_rejects_paid_charge(self):
        headers = {'access_token': provider.API_KEY, 'Idempotency-Key': 'cancel'}
        payment = self.request('/payments', {'customer': 'cus_000006360414', 'value': 42}, headers)
        path = '/payments/' + payment['id']
        self.request(path, method='DELETE', expected=401)
        deleted = self.request(path, headers=headers, method='DELETE')
        self.assertTrue(deleted['deleted'])
        self.assertEqual(deleted, self.request(path, headers=headers, method='DELETE'))
        paid = json.loads((provider.ROOT / 'history-payments.json').read_text())[0]
        self.request('/payments/' + paid['id'], headers=headers, method='DELETE', expected=409)

    def test_confirmation_calls_real_webhook_contract_and_retains_reconciliation_state(self):
        payment = self.request('/payments', {'customer': 'cus_000006360414', 'value': 42}, {'access_token': provider.API_KEY, 'Idempotency-Key': 'confirm'})
        with patch.object(provider.urllib.request, 'urlopen') as webhook:
            provider.transition_payment(payment['id'], 'CONFIRMED')
            request = webhook.call_args.args[0]
            self.assertEqual(provider.PRODUCT_URL + '/webhooks/asaas', request.full_url)
            self.assertEqual(provider.WEBHOOK_TOKEN, request.get_header('Asaas-access-token'))
            self.assertEqual('PAYMENT_CONFIRMED', json.loads(request.data)['event'])
        self.assertEqual('CONFIRMED', self.request('/payments/' + payment['id'], headers={'access_token': provider.API_KEY})['status'])
        with self.assertRaises(ValueError):
            provider.transition_payment(payment['id'], 'OVERDUE')

    def test_control_logout_revokes_session_and_missing_csrf_fails(self):
        token = 'test-session'
        with provider.connect() as db:
            db.execute('INSERT INTO session VALUES (?, ?, ?)', (hashlib.sha256(token.encode()).hexdigest(), 'test-csrf', 9999999999))
        cookie = 'natiart_qa_session=' + token
        self.request('/review/logout', {}, {'Cookie': cookie}, 403)
        # A form is required for cookie authorization; disable redirects to inspect it.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args):
                return None
        opener = urllib.request.build_opener(NoRedirect())
        request = urllib.request.Request(self.origin + '/review/logout', data=b'csrf=test-csrf', headers={'Content-Type': 'application/x-www-form-urlencoded', 'Cookie': cookie})
        with self.assertRaises(urllib.error.HTTPError) as redirect:
            opener.open(request)
        self.assertEqual(303, redirect.exception.code)
        redirect.exception.close()
        self.request('/review/logout', {}, {'Cookie': cookie}, 403)
        with provider.connect() as db:
            self.assertEqual(0, db.execute('SELECT COUNT(*) FROM session').fetchone()[0])


class GalleryTest(unittest.TestCase):
    def test_gallery_matches_seed_and_has_unique_owned_files(self):
        gallery = list(prepare_images.gallery())
        self.assertEqual(34, len(gallery))
        self.assertEqual(32, len({entry[0] for entry in gallery}))
        self.assertEqual(34, len({entry[3] for entry in gallery}))
        seed = (prepare_images.ROOT.parent / 'backend/product-service/src/main/resources/qa-data.sql').read_text()
        self.assertTrue(seed.endswith(prepare_images.image_sql()))
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory)
            prepare_images.prepare(destination)
            for _, _, asset, key, _ in gallery:
                self.assertEqual((prepare_images.ROOT / 'assets' / (asset + '.webp')).read_bytes(), (destination / key).read_bytes())


if __name__ == '__main__':
    unittest.main()
