"""Full QA journey and restart contract against an isolated Docker Compose project."""
import argparse
import base64
from decimal import Decimal
import json
import http.cookiejar
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
PASSWORD = 'password'
CUSTOMER = 'john.doe@gmail.com'
ADMIN = 'admin@gmail.com'


class Smoke:
    def __init__(self, project, port):
        self.project = project
        self.origin = f'http://localhost:{port}'
        self.env = dict(os.environ, QA_PORT=str(port), QA_PUBLIC_URL=self.origin,
                        NATIART_QA_CONTROL_TOKEN='qa-smoke-control',
                        SAAS_SECURITY_RATE_LIMIT_MAX_REQUESTS_PER_MINUTE='100')
        self.control = {'X-NatiArt-QA-Token': self.env['NATIART_QA_CONTROL_TOKEN']}
        self.command = ['docker', 'compose', '-p', project, '-f', str(ROOT / 'compose.yaml')]

    def compose(self, *args):
        subprocess.run(self.command + list(args), cwd=ROOT, env=self.env, check=True)

    def request(self, path, data=None, token=None, method=None, headers=None, expected=200, binary=False, opener=None):
        supplied = dict(headers or {})
        if token:
            supplied['Authorization'] = 'Bearer ' + token
        if isinstance(data, (dict, list)):
            data = json.dumps(data).encode()
            supplied['Content-Type'] = 'application/json'
        request = urllib.request.Request(self.origin + path, data=data, headers=supplied, method=method)
        try:
            response = (opener.open if opener else urllib.request.urlopen)(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            payload = response.read()
            assert response.status == expected, f'{request.get_method()} {path}: expected {expected}, got {response.status}; {payload[:300]!r}'
            if binary or not payload or 'json' not in response.headers.get('Content-Type', ''):
                return payload
            return json.loads(payload)

    def login(self, username, password=PASSWORD):
        return self.request('/server/directory/login', {'username': username, 'password': password})['accessToken']

    def gallery(self, admin):
        products = self.request('/server/product/products?size=100', token=admin)
        assert len(products) == 32
        assert sum(len(product['images']) for product in products) == 34
        images = {}
        for product in products:
            for image in product['images']:
                data = self.request('/server/product/images?' + urllib.parse.urlencode({'path': image}), token=admin, binary=True)
                assert data[:4] == b'RIFF' and data[8:12] == b'WEBP'
                images[image] = data
        return products, images

    def multipart(self, fields, files):
        boundary = 'natiart-qa-' + uuid.uuid4().hex
        parts = []
        for name, value in fields.items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\nContent-Type: application/json\r\n\r\n'.encode() + json.dumps(value).encode() + b'\r\n')
        for name, filename, kind, content in files:
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\nContent-Type: {kind}\r\n\r\n'.encode() + content + b'\r\n')
        return b''.join(parts) + f'--{boundary}--\r\n'.encode(), {'Content-Type': 'multipart/form-data; boundary=' + boundary}

    def checkout(self, customer, product, profile, personalization=None):
        item = {'productId': product['id'], 'quantity': 1}
        if personalization:
            item['personalization'] = {'personalizationOptions': personalization}
        quote = self.request('/server/product/shipping/quote', {'zipCode': profile['zipCode'], 'items': [item]}, customer)
        assert quote['shippingAmount'] == 18.90 and quote['serviceId'] == '1'
        body = {key: profile[key] for key in ['firstname', 'lastname', 'phone', 'country', 'state', 'city', 'neighborhood', 'zipCode', 'street', 'houseNumber', 'complement']}
        body.update(email=CUSTOMER, items=[dict(item, price=quote['items'][0]['unitPrice'])],
                    shippingQuoteId=quote['quoteId'], deliveryAmount=quote['shippingAmount'], totalAmount=quote['totalAmount'])
        key = 'qa-order-' + uuid.uuid4().hex
        order = self.request('/server/product/orders/create', body, customer, headers={'Idempotency-Key': key})
        replay = self.request('/server/product/orders/create', body, customer, headers={'Idempotency-Key': key})
        assert order['id'] == replay['id'] and order['status'] == 'PENDING'
        body = {'paymentProcessor': 'ASAAS', 'customerId': order['ownerExternalId'], 'value': order['totalAmount'], 'billingType': 'PIX', 'orderId': order['id']}
        key = 'qa-payment-' + uuid.uuid4().hex
        payment = self.request('/server/product/payments/create', body, customer, headers={'Idempotency-Key': key})
        replay = self.request('/server/product/payments/create', body, customer, headers={'Idempotency-Key': key})
        assert payment['paymentId'] == replay['paymentId']
        qr = self.request('/server/product/payments/' + payment['paymentId'] + '/pix-qr-code', token=customer)
        assert 'NOT-A-PAYMENT' in qr['payload'] and base64.b64decode(qr['encodedImage']).startswith(b'\x89PNG')
        return order, payment['paymentId']

    def guest_journey(self, product, profile, admin):
        print('Checking guest checkout, private capabilities, email tracking and account claiming...', flush=True)
        directory, commerce = '/server/directory', '/server/product'
        email = 'guest.qa@example.invalid'
        profile = {key: value for key, value in profile.items() if key not in ['id', 'version']}
        def browser():
            return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        first, other = browser(), browser()
        def start(opener):
            session = self.request(directory + '/guest/session', {}, headers={'X-Guest-Request': '1'}, opener=opener)
            headers = {'X-Guest-CSRF': session['csrfToken']}
            session = self.request(directory + '/guest/session/details', {'email': email, 'profile': profile, 'remember': True}, headers=headers, opener=opener)
            deadline = time.monotonic() + 30
            while not session['externalId']:
                assert time.monotonic() < deadline, 'Guest payer was not provisioned'
                time.sleep(.25)
                session = self.request(directory + '/guest/session', opener=opener)
            assert session['remembered'] and session['customerId']
            return session, headers
        session, headers = start(first)
        stranger, stranger_headers = start(other)
        assert session['customerId'] != stranger['customerId'] and session['externalId'] != stranger['externalId']
        self.request(commerce + '/account/orders', opener=first, expected=403)
        self.request(commerce + '/guest/shipping/quote', {'zipCode': profile['zipCode'], 'items': []}, headers={'X-Guest-CSRF': 'wrong'}, opener=first, expected=403)
        data, multipart_headers = self.multipart({}, [('file', 'guest.png', 'image/png', (ROOT / 'qa/assets/demo-qr.png').read_bytes())])
        upload = self.request(commerce + '/guest/customer/uploads', data, headers=dict(headers, **multipart_headers), opener=first)
        item = {'productId': product['id'], 'quantity': 1, 'personalization': {'personalizationOptions': {'CUSTOM_IMAGE': upload['uploadId']}}}
        quote = self.request(commerce + '/guest/shipping/quote', {'zipCode': profile['zipCode'], 'items': [item]}, headers=headers, opener=first)
        body = {key: profile.get(key) for key in ['firstname', 'lastname', 'phone', 'country', 'state', 'city', 'neighborhood', 'zipCode', 'street', 'houseNumber', 'complement']}
        body.update(email='forged@example.invalid', items=[item], shippingQuoteId=quote['quoteId'])
        order_key, payment_key = str(uuid.uuid4()), str(uuid.uuid4())
        attempt = {'username': 'guest:' + session['id'], 'orderRequest': body, 'orderIdempotencyKey': order_key,
                   'paymentIdempotencyKey': payment_key, 'currentOrder': None, 'paymentId': None}
        self.request(directory + '/guest/session/attempt', {'attemptJson': json.dumps(attempt)}, headers=headers, opener=first, expected=204)
        order = self.request(commerce + '/guest/orders/create', body, headers=dict(headers, **{'Idempotency-Key': order_key}), opener=first)
        assert order['email'] == email and order['ownerExternalId'] == session['externalId']
        replay = self.request(commerce + '/guest/orders/create', body, headers=dict(headers, **{'Idempotency-Key': order_key}), opener=first)
        assert replay['id'] == order['id']
        self.request(commerce + '/guest/orders/' + order['id'], opener=other, expected=403)
        payment_body = {'paymentProcessor': 'ASAAS', 'customerId': 'forged-payer', 'value': order['totalAmount'], 'billingType': 'PIX', 'orderId': order['id']}
        payment = self.request(commerce + '/guest/payments/create', payment_body, headers=dict(headers, **{'Idempotency-Key': payment_key}), opener=first)
        replay = self.request(commerce + '/guest/payments/create', payment_body, headers=dict(headers, **{'Idempotency-Key': payment_key}), opener=first)
        assert replay['paymentId'] == payment['paymentId'] and payment['customerId'] == session['externalId']
        self.request(commerce + '/guest/payments/' + payment['paymentId'] + '/status', opener=other, expected=403)
        qr = self.request(commerce + '/guest/payments/' + payment['paymentId'] + '/pix-qr-code', opener=first)
        assert 'NOT-A-PAYMENT' in qr['payload']
        attempt.update(currentOrder=order, paymentId=payment['paymentId'])
        self.request(directory + '/guest/session/attempt', {'attemptJson': json.dumps(attempt)}, headers=headers, opener=first, expected=204)
        assert json.loads(self.request(directory + '/guest/session', opener=first)['attemptJson'])['paymentId'] == payment['paymentId']
        self.request(directory + '/login', {'username': email, 'password': PASSWORD}, expected=401)
        def proof():
            self.request(directory + '/checkout-claim/request', {'email': email}, expected=202)
            inbox = self.request('/qa/notifications', headers=self.control)
            link = next(message['reset_link'] for message in inbox if message['recipient'] == email)
            assert link.startswith(self.origin + '/en/claim-orders#token=')
            return urllib.parse.parse_qs(urllib.parse.urlsplit(link).fragment)['token'][0]
        tracking_proof = proof()
        reader = browser()
        self.request(directory + '/checkout-claim/track', {'token': tracking_proof}, opener=reader, expected=204)
        self.request(directory + '/checkout-claim/inspect', {'token': tracking_proof}, expected=400)
        visible = self.request(commerce + '/guest/tracking/orders', opener=reader)
        assert [entry['id'] for entry in visible] == [order['id']]
        self.request(commerce + '/guest/orders/' + order['id'], method='DELETE', headers=headers, opener=reader, expected=403)
        self.request(commerce + '/guest/tracking/payments/' + payment['paymentId'] + '/status', opener=reader)
        self.request(directory + '/login', {'username': email, 'password': PASSWORD}, expected=401)
        activation = proof()
        assert not self.request(directory + '/checkout-claim/inspect', {'token': activation})['existingVerifiedAccount']
        self.request(directory + '/checkout-claim/confirm', {'token': activation, 'password': 'NatiArtGuest9!', 'passwordConfirmation': 'NatiArtGuest9!'}, expected=204)
        account_token = self.login(email, 'NatiArtGuest9!')
        deadline = time.monotonic() + 30
        while True:
            history = self.request(commerce + '/account/orders', token=account_token)
            if any(entry['id'] == order['id'] for entry in history): break
            assert time.monotonic() < deadline, 'Claim was not durably delivered'; time.sleep(.25)
        claimed = self.request(commerce + '/account/orders/' + order['id'], token=account_token)
        assert claimed['ownerExternalId'] == session['externalId'] and claimed['paymentId'] == payment['paymentId']
        self.request(commerce + '/guest/orders/' + order['id'], opener=first, expected=403)
        self.request(commerce + '/account/payments/' + payment['paymentId'] + '/status', token=account_token)
        self.request('/qa/confirm/' + payment['paymentId'], {}, headers=self.control)
        assert self.request(commerce + '/account/orders/' + order['id'], token=account_token)['status'] == 'PAID'
        artwork = self.request(f"{commerce}/admin/orders/{order['id']}/items/{claimed['items'][0]['id']}/artwork", token=admin, binary=True)
        assert artwork[:4] == b'RIFF'
        # The same email can buy as a guest again without replacing its verified account.
        original_profile = self.request(directory + '/users/current', token=account_token)['profile']
        again, again_headers = start(browser())
        assert again['customerId'] != session['customerId']
        verified = proof()
        assert self.request(directory + '/checkout-claim/inspect', {'token': verified})['existingVerifiedAccount']
        self.request(directory + '/checkout-claim/confirm', {'token': verified, 'password': 'WrongPassword1!', 'passwordConfirmation': 'WrongPassword1!'}, expected=400)
        self.request(directory + '/checkout-claim/confirm', {'token': verified, 'password': 'NatiArtGuest9!', 'passwordConfirmation': 'NatiArtGuest9!'}, expected=204)
        assert self.request(directory + '/users/current', token=account_token)['profile'] == original_profile

    def run(self):
        print('Checking fixed catalog, gallery and both locale bundles...', flush=True)
        for locale in ['en', 'pt-BR']:
            assert b'<html' in self.request('/' + locale + '/dashboard', binary=True)
        assert b'Test the whole journey' in self.request('/qa/', binary=True)
        self.request('/qa/notifications', expected=403)
        assert b'<html' not in self.request('/qa/internal/reset-payments', {}, headers=self.control, expected=404, binary=True)
        admin, customer = self.login(ADMIN), self.login(CUSTOMER)
        products, images = self.gallery(admin)
        assert len(self.request('/server/product/categories?size=100', token=admin)) == 24
        assert len(self.request('/server/product/packages?size=100', token=admin)) == 24
        seeded_orders = self.request('/server/product/admin/orders?size=100', token=admin)
        assert len(seeded_orders) == 35
        provider_payments = {value['id']: value for value in self.request('/qa/payments', headers=self.control)}
        assert len(provider_payments) == 29
        for seeded in seeded_orders:
            subtotal = sum(Decimal(str(item['price'])) * item['quantity'] for item in seeded['items'])
            assert subtotal + Decimal(str(seeded['deliveryAmount'])) == Decimal(str(seeded['totalAmount']))
            if seeded.get('paymentId'):
                payment = provider_payments[seeded['paymentId']]
                assert payment['customer'] == seeded['ownerExternalId']
                assert Decimal(str(payment['value'])) == Decimal(str(seeded['totalAmount']))
        for username, count in [('maria@natiart.local', 8), ('ana@natiart.local', 5), ('newbuyer@natiart.local', 0)]:
            buyer = self.login(username)
            orders = self.request('/server/product/orders?size=100', token=buyer)
            assert len(orders) == count
            assert self.request('/server/product/cart', token=buyer)
            if username == 'maria@natiart.local':
                pending = next(order for order in orders if order['status'] == 'PENDING')
                qr = self.request('/server/product/payments/' + pending['paymentId'] + '/pix-qr-code', token=buyer)
                assert 'NOT-A-PAYMENT' in qr['payload']
                self.request('/qa/confirm/' + pending['paymentId'], {}, headers=self.control)
                assert self.request('/server/product/orders/' + pending['id'], token=buyer)['status'] == 'PAID'
            if username == 'ana@natiart.local':
                processing = next(order for order in orders if order['status'] == 'PROCESSING')
                item = processing['items'][0]
                data = self.request(f"/server/product/admin/orders/{processing['id']}/items/{item['id']}/artwork", token=admin, binary=True)
                assert data == (ROOT / 'qa/assets/art-plate.webp').read_bytes()
        self.request('/server/directory/login', {'username': 'disabled@natiart.local', 'password': PASSWORD}, expected=401)
        original_orders = self.request('/server/product/orders?size=100', token=customer)
        assert len(original_orders) == 22
        current = self.request('/server/directory/users/current', token=customer)
        profile = current['profile']
        product = next(product for product in products if product['label'] == 'Personalized Anniversary Plate')
        stock = product['stockQuantity']
        print('Checking artwork, quote, idempotent order/payment, PIX and fulfillment...', flush=True)
        data, headers = self.multipart({}, [('file', 'demo.png', 'image/png', (ROOT / 'qa/assets/demo-qr.png').read_bytes())])
        upload = self.request('/server/product/customer/uploads', data, customer, headers=headers)
        order, payment = self.checkout(customer, product, profile, {'CUSTOM_IMAGE': upload['uploadId'], 'GOLDEN_BORDER': 'true'})
        self.request('/qa/confirm/' + payment, {}, headers=self.control)
        confirmed = self.request('/server/product/orders/' + order['id'], token=customer)
        assert confirmed['status'] == 'PAID'
        assert confirmed['items'][0]['personalization']['personalizationOptions']['CUSTOM_IMAGE'] == upload['uploadId']
        artwork = self.request(f"/server/product/admin/orders/{order['id']}/items/{confirmed['items'][0]['id']}/artwork", token=admin, binary=True)
        assert artwork[:4] == b'RIFF'
        for status in ['PROCESSING', 'SHIPPED', 'DELIVERED']:
            updated = self.request('/server/product/admin/orders/' + order['id'] + '/status', {'status': status}, admin, 'PATCH')
            assert updated['status'] == status
        expired_order, expired_payment = self.checkout(customer, product, profile)
        self.request('/qa/expire/' + expired_payment, {}, headers=self.control)
        assert next(value for value in self.request('/qa/payments', headers=self.control) if value['id'] == expired_payment)['status'] == 'OVERDUE'
        cancelled = self.request('/server/product/orders/' + expired_order['id'], token=customer, method='DELETE')
        assert cancelled['status'] == 'CANCELLED'
        print('Checking account update, registration and one-use recovery...', flush=True)
        updated_profile = dict(profile, houseNumber='999')
        self.request('/server/directory/users/current/profile', {'profile': updated_profile, 'currentPassword': PASSWORD}, customer, 'PUT')
        new_profile = {key: value for key, value in profile.items() if key not in ['id', 'version']}
        self.request('/server/directory/register-user', {'username': 'new.qa@example.invalid', 'password': 'NatiArtQa9!', 'profile': new_profile})
        new_token = self.login('new.qa@example.invalid', 'NatiArtQa9!')
        deadline = time.monotonic() + 30
        while not self.request('/server/directory/users/current', token=new_token)['externalId']:
            assert time.monotonic() < deadline, 'Registered customer was not provisioned'
            time.sleep(1)
        self.request('/server/directory/password-reset/request', {'username': CUSTOMER}, expected=202)
        inbox = self.request('/qa/notifications', headers=self.control)
        assert len(inbox) == 1 and inbox[0]['reset_link'].startswith(self.origin + '/en/reset-password#token=')
        reset_token = urllib.parse.parse_qs(urllib.parse.urlsplit(inbox[0]['reset_link']).fragment)['token'][0]
        reset = {'token': reset_token, 'password': 'NatiArtQa9!', 'passwordConfirmation': 'NatiArtQa9!'}
        self.request('/server/directory/password-reset', reset, expected=204)
        self.request('/server/directory/password-reset', reset, expected=400)
        customer = self.login(CUSTOMER, 'NatiArtQa9!')
        self.request('/server/product/cart/item/' + product['id'] + '/add', {}, customer)
        self.guest_journey(product, profile, admin)
        print('Changing a seeded product and replacing a seeded image before restart...', flush=True)
        changed = dict(self.request('/server/product/products/' + product['id'], token=admin), label='Disposable QA edit', images=[])
        data, headers = self.multipart({'productDto': changed}, [('newImages', 'new.png', 'image/png', (ROOT / 'qa/assets/demo-qr.png').read_bytes())])
        changed = self.request('/server/product/products/' + product['id'], data, admin, 'PUT', headers=headers)
        assert changed['label'] == 'Disposable QA edit' and changed['images'][0] not in images
        new_image = changed['images'][0]
        self.compose('restart')
        self.compose('up', '-d', '--wait', '--wait-timeout', '180', '--no-build')
        print('Checking that restart discarded every modification...', flush=True)
        admin, customer = self.login(ADMIN), self.login(CUSTOMER)
        restored, restored_images = self.gallery(admin)
        assert restored_images == images
        restored_product = next(value for value in restored if value['id'] == product['id'])
        assert restored_product['label'] == product['label'] and restored_product['stockQuantity'] == stock
        assert self.request('/server/directory/users/current', token=customer)['profile']['houseNumber'] == profile['houseNumber']
        assert self.request('/server/product/cart', token=customer) == []
        assert {value['id'] for value in self.request('/server/product/orders?size=100', token=customer)} == {value['id'] for value in original_orders}
        assert self.request('/qa/notifications', headers=self.control) == []
        assert all(value['id'] != payment for value in self.request('/qa/payments', headers=self.control))
        assert next(value for value in self.request('/qa/payments', headers=self.control) if value['id'] == 'pay_qa_seed_maria_0')['status'] == 'PENDING'
        self.request('/server/directory/login', {'username': 'new.qa@example.invalid', 'password': 'NatiArtQa9!'}, expected=401)
        self.request('/server/product/images?' + urllib.parse.urlencode({'path': new_image}), token=admin, expected=404)
        # A fresh purchase proves reset also restored the integration, not only SQL rows.
        fresh, payment = self.checkout(customer, restored_product, profile)
        self.request('/qa/confirm/' + payment, {}, headers=self.control)
        assert self.request('/server/product/orders/' + fresh['id'], token=customer)['status'] == 'PAID'
        print('PASS: catalog/images, checkout/artwork, providers, accounts/recovery and fixed-data restart.', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--no-build', action='store_true')
    parser.add_argument('--project', default='natiart-qa-smoke-' + uuid.uuid4().hex[:10])
    parser.add_argument('--port', type=int)
    parser.add_argument('--keep', action='store_true', help='Keep the isolated local stack for browser inspection')
    args = parser.parse_args()
    assert args.project.startswith('natiart-qa-smoke-'), 'Only disposable smoke projects may be managed'
    if args.port is None:
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            args.port = sock.getsockname()[1]
    smoke = Smoke(args.project, args.port)
    try:
        options = ['up', '-d', '--wait', '--wait-timeout', '240']
        options.append('--no-build' if args.no_build else '--build')
        smoke.compose(*options)
        smoke.run()
    except BaseException:
        smoke.compose('logs', '--tail=80')
        raise
    finally:
        if args.keep:
            print('Kept QA at ' + smoke.origin, flush=True)
        else:
            smoke.compose('down', '--volumes', '--remove-orphans')


if __name__ == '__main__':
    main()
