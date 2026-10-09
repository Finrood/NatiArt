-- Fictional QA data. Restored from this committed file on every QA restart.
-- Local-only seed data. Local dev passwords (see the comments below) are
-- throwaway credentials; real deployments must never rely on seeded users.
INSERT INTO public.role (id, version, label, description, created_at, updated_at, active, deactivated_at)
VALUES ('a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6', 0, 'USER', 'Standard user role', '2024-07-01T00:00:00Z',
        '2024-07-01T00:00:00Z', true, NULL),
       ('q7r8s9t0-u1v2-w3x4-y5z6-a7b8c9d0e1f2', 0, 'ADMIN', 'Administrator role', '2024-07-01T00:00:00Z',
        '2024-07-01T00:00:00Z', true, NULL);

INSERT INTO public.users (id, version, username, password_hash, email_confirmed, created_at, updated_at, active,
                          deactivated_at, role_id)
VALUES ('789e4567-e89b-12d3-a456-426614174000', -- id
        1, -- version
        'john.doe@gmail.com', -- username
        '$2a$10$xMd..Fbi1UXlNgdQXqiaqO9bardQ/FlQMlydljm1aFBvUC4SwZ2gC', -- bcrypt hash of the local-only password "password"
        false, -- emailConfirmed
        '2023-05-15T10:00:00Z', -- createdAt
        '2023-05-15T10:00:00Z', -- updatedAt
        true, -- active
        NULL, -- deactivatedAt (assuming null means not deactivated)
        'a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6'),
       ('889e4567-e89b-12d3-a456-426614174000', -- id
        1, -- version
        'admin@gmail.com', -- username
        '$2a$10$xMd..Fbi1UXlNgdQXqiaqO9bardQ/FlQMlydljm1aFBvUC4SwZ2gC', -- bcrypt hash of the local-only password "password"
        false, -- emailConfirmed
        '2023-05-15T10:00:00Z', -- createdAt
        '2023-05-15T10:00:00Z', -- updatedAt
        true, -- active
        NULL, -- deactivatedAt (assuming null means not deactivated)
        'q7r8s9t0-u1v2-w3x4-y5z6-a7b8c9d0e1f2');

INSERT INTO public.profile (id, version, firstname, lastname, cpf, phone, country, state, city, zip_code, street,
                            complement, user_id, created_at, updated_at)
VALUES ('1d17854b-ca2b-42f1-a1fc-07d1bcce85f9', -- id
        0, -- version
        'John', -- firstname
        'Doe', -- lastname
        '00000000011', -- cpf
        '123-456-7890', -- phone
        'USA', -- country
        'CA', -- state
        'Los Angeles', -- city
        '90001', -- zipCode
        '123 Main St', -- street
        'Apt 4B', -- complement
        '789e4567-e89b-12d3-a456-426614174000', -- user_id
        '2023-05-15T10:00:00Z', -- createdAt
        '2023-05-15T10:00:00Z' -- updatedAt
       );

INSERT INTO external_user(id, user_id, payment_processor, external_id)
VALUES ('889e4567-e89b-12d3-a456-426614174756',
        '789e4567-e89b-12d3-a456-426614174000',
        'ASAAS',
        'cus_000006360414');

UPDATE profile SET cpf='11144477735', country='Brazil', state='SC', city='Florianópolis', neighborhood='Centro', zip_code='88010000', street='Rua de Demonstração', complement='Apto 4B', phone='48999990000' WHERE user_id='789e4567-e89b-12d3-a456-426614174000';
INSERT INTO profile (id, version, firstname, lastname, cpf, phone, country, state, city, neighborhood, zip_code, street, complement, user_id, created_at, updated_at) VALUES ('bb8177b9-0616-5a80-bab5-bb5d136606e3', 0, 'NatiArt', 'Admin', '11144477735', '48999990001', 'Brazil', 'SC', 'Florianópolis', 'Centro', '88010000', 'Rua de Demonstração', 'Sala 2', '889e4567-e89b-12d3-a456-426614174000', DATEADD('DAY', 0, CURRENT_TIMESTAMP), DATEADD('DAY', 0, CURRENT_TIMESTAMP));
INSERT INTO external_user (id, user_id, payment_processor, external_id) VALUES ('fd6f1b96-4e87-58e6-bb59-88096c672e7b', '889e4567-e89b-12d3-a456-426614174000', 'ASAAS', 'cus_local_admin');

UPDATE profile SET house_number='123' WHERE user_id='789e4567-e89b-12d3-a456-426614174000';
UPDATE profile SET house_number='45', cpf='52998224725' WHERE user_id='889e4567-e89b-12d3-a456-426614174000';

-- Mature QA accounts: returning buyers, a new buyer and a disabled account.
INSERT INTO users (id, version, username, password_hash, email_confirmed, active, role_id) VALUES ('364aeae5-37ee-56ce-90f3-9ef6547ab5b1', 0, 'maria@natiart.local', '$2a$10$xMd..Fbi1UXlNgdQXqiaqO9bardQ/FlQMlydljm1aFBvUC4SwZ2gC', true, true, 'a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6');
INSERT INTO profile (id, version, firstname, lastname, cpf, phone, country, state, city, neighborhood, zip_code, street, house_number, complement, user_id) VALUES ('8d899e85-a7db-5829-9e1e-dd43192ba13e', 0, 'Maria', 'Oliveira', '12345678909', '11999990000', 'Brazil', 'SP', 'São Paulo', 'Centro', '01310930', 'Rua de Teste', '100', 'Apto 1', '364aeae5-37ee-56ce-90f3-9ef6547ab5b1');
INSERT INTO external_user (id, user_id, payment_processor, external_id) VALUES ('f90e711a-afec-56d4-a8fe-61c57533afaf', '364aeae5-37ee-56ce-90f3-9ef6547ab5b1', 'ASAAS', 'cus_qa_seed_maria');
UPDATE users SET created_at=DATEADD('DAY', -180, CURRENT_TIMESTAMP), updated_at=CURRENT_TIMESTAMP WHERE id='364aeae5-37ee-56ce-90f3-9ef6547ab5b1';
UPDATE profile SET created_at=DATEADD('DAY', -180, CURRENT_TIMESTAMP), updated_at=CURRENT_TIMESTAMP WHERE user_id='364aeae5-37ee-56ce-90f3-9ef6547ab5b1';
INSERT INTO users (id, version, username, password_hash, email_confirmed, active, role_id) VALUES ('62d5536b-39eb-543c-9790-4ab4b4fdd30c', 0, 'ana@natiart.local', '$2a$10$xMd..Fbi1UXlNgdQXqiaqO9bardQ/FlQMlydljm1aFBvUC4SwZ2gC', true, true, 'a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6');
INSERT INTO profile (id, version, firstname, lastname, cpf, phone, country, state, city, neighborhood, zip_code, street, house_number, complement, user_id) VALUES ('9e6f49d6-7e93-59e0-ae7c-c603be9cf121', 0, 'Ana', 'Lima', '98765432100', '41999990001', 'Brazil', 'PR', 'Curitiba', 'Centro', '80010000', 'Rua de Teste', '101', 'Apto 2', '62d5536b-39eb-543c-9790-4ab4b4fdd30c');
INSERT INTO external_user (id, user_id, payment_processor, external_id) VALUES ('b55c7c3c-261c-5555-9469-ca1209532020', '62d5536b-39eb-543c-9790-4ab4b4fdd30c', 'ASAAS', 'cus_qa_seed_ana');
UPDATE users SET created_at=DATEADD('DAY', -150, CURRENT_TIMESTAMP), updated_at=CURRENT_TIMESTAMP WHERE id='62d5536b-39eb-543c-9790-4ab4b4fdd30c';
UPDATE profile SET created_at=DATEADD('DAY', -150, CURRENT_TIMESTAMP), updated_at=CURRENT_TIMESTAMP WHERE user_id='62d5536b-39eb-543c-9790-4ab4b4fdd30c';
INSERT INTO users (id, version, username, password_hash, email_confirmed, active, role_id) VALUES ('de10ccec-be41-51b5-bbd5-c3ec3a979aef', 0, 'newbuyer@natiart.local', '$2a$10$xMd..Fbi1UXlNgdQXqiaqO9bardQ/FlQMlydljm1aFBvUC4SwZ2gC', true, true, 'a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6');
INSERT INTO profile (id, version, firstname, lastname, cpf, phone, country, state, city, neighborhood, zip_code, street, house_number, complement, user_id) VALUES ('0da0948f-16a0-57e8-ae2e-3249bb4cc181', 0, 'Rafael', 'Souza', '11122233396', '81999990002', 'Brazil', 'PE', 'Recife', 'Centro', '50010000', 'Rua de Teste', '102', 'Apto 3', 'de10ccec-be41-51b5-bbd5-c3ec3a979aef');
INSERT INTO external_user (id, user_id, payment_processor, external_id) VALUES ('dbcd46d9-b84c-5ceb-b44f-9ece200d1d65', 'de10ccec-be41-51b5-bbd5-c3ec3a979aef', 'ASAAS', 'cus_qa_seed_newbuyer');
UPDATE users SET created_at=DATEADD('DAY', -120, CURRENT_TIMESTAMP), updated_at=CURRENT_TIMESTAMP WHERE id='de10ccec-be41-51b5-bbd5-c3ec3a979aef';
UPDATE profile SET created_at=DATEADD('DAY', -120, CURRENT_TIMESTAMP), updated_at=CURRENT_TIMESTAMP WHERE user_id='de10ccec-be41-51b5-bbd5-c3ec3a979aef';
INSERT INTO users (id, version, username, password_hash, email_confirmed, active, role_id) VALUES ('70c28aad-b538-5ecf-9c94-fb654f263451', 0, 'disabled@natiart.local', '$2a$10$xMd..Fbi1UXlNgdQXqiaqO9bardQ/FlQMlydljm1aFBvUC4SwZ2gC', true, false, 'a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6');
INSERT INTO profile (id, version, firstname, lastname, cpf, phone, country, state, city, neighborhood, zip_code, street, house_number, complement, user_id) VALUES ('37831d5e-56da-512c-ab68-6fa9314b593d', 0, 'Carlos', 'Ramos', '22233344405', '51999990003', 'Brazil', 'RS', 'Porto Alegre', 'Centro', '90010000', 'Rua de Teste', '103', 'Apto 4', '70c28aad-b538-5ecf-9c94-fb654f263451');
INSERT INTO external_user (id, user_id, payment_processor, external_id) VALUES ('51da791b-3ad4-5ab9-82e8-6501c0c4d0de', '70c28aad-b538-5ecf-9c94-fb654f263451', 'ASAAS', 'cus_qa_seed_disabled');
UPDATE users SET created_at=DATEADD('DAY', -90, CURRENT_TIMESTAMP), updated_at=CURRENT_TIMESTAMP, deactivated_at=DATEADD('DAY', -7, CURRENT_TIMESTAMP) WHERE id='70c28aad-b538-5ecf-9c94-fb654f263451';
UPDATE profile SET created_at=DATEADD('DAY', -90, CURRENT_TIMESTAMP), updated_at=CURRENT_TIMESTAMP WHERE user_id='70c28aad-b538-5ecf-9c94-fb654f263451';
