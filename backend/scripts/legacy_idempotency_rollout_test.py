#!/usr/bin/env python3
"""Rehearse the scoped legacy SQL; this is not a complete release upgrade test."""
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SQL = (ROOT / 'backend/product-service/docs/sql/ca52-legacy-idempotency.sql').read_text()
# Scoped historical order shape from CustomerOrder at 0c0156fc^ (before payment
# idempotency). Other application tables are deliberately outside this fixture.
BASE = '''CREATE TABLE customer_order (
 id varchar(255) PRIMARY KEY, version bigint, firstname varchar(255) NOT NULL,
 lastname varchar(255) NOT NULL, email varchar(255) NOT NULL, phone varchar(255),
 country varchar(255), state varchar(255), city varchar(255), neighborhood varchar(255),
 zip_code varchar(255), street varchar(255), complement varchar(255),
 order_date timestamptz NOT NULL, delivery_amount numeric(10,2) NOT NULL,
 total_amount numeric(10,2) NOT NULL, status varchar(255), owner_external_id varchar(255) NOT NULL);
 INSERT INTO customer_order(id,firstname,lastname,email,order_date,delivery_amount,total_amount,status,owner_external_id)
 VALUES('legacy-order','Fixture','Customer','fixture@example.test',now(),5,20,'PENDING','legacy-owner');'''


def run(*args, **kwargs):
    return subprocess.run(args, text=True, check=True, **kwargs)


def main():
    name = 'natiart-ca52-' + uuid.uuid4().hex[:12]
    run('docker', 'run', '-d', '--name', name, '-e', 'POSTGRES_PASSWORD=inert-fixture-password',
        'postgres:17-alpine', stdout=subprocess.DEVNULL)

    def sql(query, success=True):
        result = subprocess.run(['docker', 'exec', '-i', name, 'psql', '-U', 'postgres', '-v',
                                 'ON_ERROR_STOP=1', '-At'], input=query, text=True, capture_output=True)
        if (result.returncode == 0) != success:
            raise RuntimeError('Unexpected scoped rollout result: ' + result.stderr)
        return result.stdout.strip()

    def reset():
        sql('DROP SCHEMA public CASCADE; CREATE SCHEMA public;' + BASE)

    try:
        # Initialization uses a temporary socket-only server; wait for final TCP readiness.
        run('docker', 'exec', name, 'sh', '-c',
            'for i in $(seq 1 60); do pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1 && exit 0; sleep 1; done; exit 1')
        reset()
        sql(SQL); sql(SQL)
        assert sql("SELECT count(*) FROM customer_order WHERE id='legacy-order' AND total_amount=20") == '1'
        insert = "INSERT INTO payment_idempotency VALUES('reservation-1','owner-1','key-1','fingerprint','SUCCEEDED','pay-1',now(),now());"
        sql(insert); sql(SQL)
        assert sql("SELECT provider_payment_id FROM payment_idempotency WHERE id='reservation-1'") == 'pay-1'
        sql(insert.replace('reservation-1', 'reservation-2'), success=False)
        sql(insert.replace('reservation-1', 'reservation-3').replace('owner-1', 'owner-2').replace('SUCCEEDED', 'UNKNOWN'), success=False)
        sql('ALTER TABLE payment_idempotency DROP CONSTRAINT ck_payment_idempotency_status;'
            'ALTER TABLE payment_idempotency ADD CONSTRAINT ck_payment_idempotency_status CHECK(true);')
        sql(SQL)
        sql(insert.replace('reservation-1', 'reservation-4').replace('owner-1', 'owner-3').replace('SUCCEEDED', 'UNKNOWN'), success=False)
        print('ok: repeat upgrade preserves records and enforces payment key/state constraints')

        reset()
        sql('CREATE TABLE payment_idempotency(id varchar(36) PRIMARY KEY);')
        sql(SQL, success=False)
        assert sql("SELECT count(*) FROM information_schema.columns WHERE table_name='customer_order' AND column_name='idempotency_key'") == '0'
        print('ok: unknown partial shape aborts atomically without adding order columns')

        reset()
        sql('ALTER TABLE customer_order ADD COLUMN idempotency_key varchar(64);')
        sql("UPDATE customer_order SET idempotency_key='duplicate';"
            "INSERT INTO customer_order SELECT 'other-order',version,firstname,lastname,email,phone,country,state,city,neighborhood,zip_code,street,complement,order_date,delivery_amount,total_amount,status,owner_external_id,idempotency_key FROM customer_order;")
        sql(SQL, success=False)
        assert sql("SELECT count(*) FROM customer_order WHERE idempotency_key='duplicate'") == '2'
        assert sql("SELECT count(*) FROM information_schema.tables WHERE table_name='payment_idempotency'") == '0'
        print('ok: duplicate orders preserved; new payment table rolled back')

        reset(); sql(SQL)
        sql('DROP INDEX uk_payment_idempotency_owner_key;')
        sql(insert)
        sql(insert.replace('reservation-1', 'reservation-2'))
        sql(SQL, success=False)
        assert sql('SELECT count(*) FROM payment_idempotency') == '2'
        print('ok: duplicate financial rows abort instead of selecting a winner')

        reset(); sql(SQL)
        sql('ALTER TABLE payment_idempotency DROP CONSTRAINT payment_idempotency_pkey;')
        sql(SQL, success=False)
        print('ok: missing financial primary key requires explicit reconciliation')

        reset()
        sql('CREATE TABLE unrelated(owner_external_id text, idempotency_key text);'
            'CREATE UNIQUE INDEX uk_customer_order_owner_idempotency ON unrelated(owner_external_id,idempotency_key);')
        sql(SQL, success=False)
        assert sql("SELECT count(*) FROM information_schema.columns WHERE table_name='customer_order' AND column_name='request_fingerprint'") == '0'
        print('ok: same-name foreign index cannot satisfy the order uniqueness guard')

        sql('DROP SCHEMA public CASCADE; CREATE SCHEMA public;')
        sql(SQL, success=False)
        assert sql("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'") == '0'
        print('ok: scoped upgrade rejects an empty application schema explicitly')
    finally:
        run('docker', 'rm', '-fv', name, stdout=subprocess.DEVNULL)


if __name__ == '__main__':
    main()
