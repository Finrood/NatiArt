INSERT INTO public.role (id, version, label, description, created_at, updated_at, active, deactivated_at) VALUES
('a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6', 0, 'USER', 'Standard user role', '2024-07-01T00:00:00Z', '2024-07-01T00:00:00Z', true, NULL),
('q7r8s9t0-u1v2-w3x4-y5z6-a7b8c9d0e1f2', 0, 'ADMIN', 'Administrator role', '2024-07-01T00:00:00Z', '2024-07-01T00:00:00Z', true, NULL);

INSERT INTO public.users (id, version, username, password_hash, email_confirmed, created_at, updated_at, active, deactivated_at, role_id) VALUES
         (
             '789e4567-e89b-12d3-a456-426614174000', -- id
             1,                                      -- version
             'john.doe@gmail.com',                   -- username
             '$2a$10$xXUJ6rhpG39.C7mXYhdXB.oq2DLVgbAIvcp2chu3uQlGj20i9E.Iq', -- password (hashed password)
             false,                                  -- emailConfirmed
             '2023-05-15T10:00:00Z',                 -- createdAt
             '2023-05-15T10:00:00Z',                 -- updatedAt
             true,                                   -- active
             NULL,                                    -- deactivatedAt (assuming null means not deactivated)
             'a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6'
         ),
         (
             '889e4567-e89b-12d3-a456-426614174000', -- id
             1,                                      -- version
             'admin@gmail.com',                   -- username
             '$2a$10$xXUJ6rhpG39.C7mXYhdXB.oq2DLVgbAIvcp2chu3uQlGj20i9E.Iq', -- password (hashed password)
             false,                                  -- emailConfirmed
             '2023-05-15T10:00:00Z',                 -- createdAt
             '2023-05-15T10:00:00Z',                 -- updatedAt
             true,                                   -- active
             NULL,                                    -- deactivatedAt (assuming null means not deactivated)
             'q7r8s9t0-u1v2-w3x4-y5z6-a7b8c9d0e1f2'
         );

INSERT INTO public.profile (id, version, firstname, lastname, cpf, phone, country, state, city, zip_code, street, complement, user_id, created_at, updated_at) VALUES (
             '1d17854b-ca2b-42f1-a1fc-07d1bcce85f9', -- id
             0,                                      -- version
             'John',                                 -- firstname
             'Doe',                                  -- lastname
             '00000000011',                              -- cpf
             '123-456-7890',                         -- phone
             'USA',                                  -- country
             'CA',                                   -- state
             'Los Angeles',                          -- city
             '90001',                                -- zipCode
             '123 Main St',                          -- street
             'Apt 4B',                               -- complement
             '789e4567-e89b-12d3-a456-426614174000', -- user_id
             '2023-05-15T10:00:00Z',                 -- createdAt
             '2023-05-15T10:00:00Z'                  -- updatedAt
         );