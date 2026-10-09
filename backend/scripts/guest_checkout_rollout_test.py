#!/usr/bin/env python3
"""Rehearse additive guest migrations against isolated pre-guest PostgreSQL tables."""
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / 'backend/directory-service/docs/migrations/20261009-guest-checkout.sql'
PRODUCT = ROOT / 'backend/product-service/docs/migrations/20261009-guest-checkout.sql'


def main():
    name = 'natiart-guest-rollout-' + uuid.uuid4().hex[:12]
    subprocess.run(['docker', 'run', '-d', '--network', 'none', '--name', name,
                    '-e', 'POSTGRES_PASSWORD=inert-fixture-password', 'postgres:17-alpine'],
                   check=True, stdout=subprocess.DEVNULL)

    def sql(schema, query, success=True):
        result = subprocess.run(['docker', 'exec', '-i', name, 'psql', '-U', 'postgres',
                                 '-v', 'ON_ERROR_STOP=1', '-At'],
                                input=f'SET search_path TO {schema};\n' + query,
                                text=True, capture_output=True)
        if (result.returncode == 0) != success:
            raise RuntimeError('Unexpected guest migration result: ' + result.stderr)
        return result.stdout.strip().splitlines()[-1]

    try:
        subprocess.run(['docker', 'exec', name, 'sh', '-c',
                        'for i in $(seq 1 60); do pg_isready -h 127.0.0.1 -U postgres '
                        '>/dev/null 2>&1 && exit 0; sleep 1; done; exit 1'], check=True)
        sql('public', '''CREATE SCHEMA directory; CREATE SCHEMA product;
          CREATE TABLE directory.asaas_provisioning_job (
            id varchar(255) PRIMARY KEY, user_id varchar(255) NOT NULL,
            payment_processor varchar(255) NOT NULL,
            UNIQUE(user_id, payment_processor));
          INSERT INTO directory.asaas_provisioning_job VALUES ('existing-job', 'existing-user', 'ASAAS');
          CREATE TABLE product.customer_order (
            id varchar(255) PRIMARY KEY, owner_external_id varchar(255) NOT NULL,
            email varchar(255) NOT NULL, order_date timestamptz NOT NULL);
          INSERT INTO product.customer_order VALUES ('existing-order', 'cus_original', 'buyer@example.test', now());''')
        for schema, migration in [('directory', DIRECTORY), ('product', PRODUCT)]:
            sql(schema, migration.read_text())
            sql(schema, migration.read_text())
        assert sql('directory', "SELECT user_id FROM asaas_provisioning_job WHERE id='existing-job'") == 'existing-user'
        assert sql('product', "SELECT owner_external_id FROM customer_order WHERE id='existing-order' AND account_owner_id IS NULL AND guest_customer_id IS NULL") == 'cus_original'

        sql('directory', "INSERT INTO guest_customer(id,email,profile_json,created_at) VALUES ('guest-1','buyer@example.test','{}',now());")
        insert_job = "INSERT INTO asaas_provisioning_job(id,user_id,guest_customer_id,payment_processor) VALUES ('guest-job',NULL,'guest-1','ASAAS');"
        sql('directory', insert_job)
        sql('directory', insert_job.replace('guest-job', 'duplicate-job'), success=False)
        sql('directory', insert_job.replace('guest-job', 'mixed-job').replace("NULL,'guest-1'", "'existing-user','guest-1'"), success=False)
        sql('directory', insert_job.replace('guest-job', 'empty-job').replace("'guest-1'", 'NULL'), success=False)
        sql('directory', insert_job.replace('guest-job', 'missing-job').replace('guest-1', 'missing-guest'), success=False)

        digest = 'a' * 64
        sql('directory', f"INSERT INTO guest_session(id,token_digest,expires_at,remembered,customer_id) VALUES ('session-1','{digest}',now()+interval '1 day',false,'guest-1');")
        sql('directory', f"INSERT INTO guest_session(id,token_digest,expires_at,remembered) VALUES ('session-2','{digest}',now(),false);", success=False)
        sql('directory', f"INSERT INTO checkout_claim(id,token_digest,email,profile_json,cutoff,expires_at,consumed_at,account_id,next_delivery_at) VALUES ('proof-1','{digest}','buyer@example.test','{{}}',now(),now()+interval '15 minutes',now(),'account-1',now());")
        sql('directory', f"INSERT INTO guest_order_session(id,token_digest,email,cutoff,expires_at) VALUES ('tracking-1','{digest}','buyer@example.test',now(),now()+interval '1 day');")
        sql('product', "INSERT INTO guest_order_claim(id,account_id,email,cutoff) VALUES ('proof-1','account-1','buyer@example.test',now());")
        sql('product', "INSERT INTO customer_order(id,owner_external_id,email,order_date,guest_customer_id,account_owner_id) VALUES ('guest-order','cus_guest','buyer@example.test',now(),'guest-1','account-1');")
        for schema, migration in [('directory', DIRECTORY), ('product', PRODUCT)]:
            sql(schema, migration.read_text())
        assert sql('directory', 'SELECT count(*) FROM guest_session') == '1'
        assert sql('directory', "SELECT account_id FROM checkout_claim WHERE id='proof-1'") == 'account-1'
        assert sql('product', "SELECT owner_external_id FROM customer_order WHERE id='guest-order' AND account_owner_id='account-1'") == 'cus_guest'
        assert sql('product', 'SELECT count(*) FROM guest_order_claim') == '1'
        print('PASS: repeat guest migrations preserve legacy and guest records, payer identities, unique capabilities and exclusive provisioning ownership.')
    finally:
        subprocess.run(['docker', 'rm', '-fv', name], check=True, stdout=subprocess.DEVNULL)


if __name__ == '__main__':
    main()
