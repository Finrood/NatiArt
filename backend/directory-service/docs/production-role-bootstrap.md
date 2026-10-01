# Production role bootstrap

The directory service creates the `USER` and `ADMIN` role rows when the
application reaches `ApplicationReadyEvent`. The operation is idempotent and
does not create users, passwords, profiles or external payment accounts.

For a first administrator, an operator must use the approved database change
process after the application has created the roles:

1. Generate a unique administrator username and password through the approved
   secret-management process. Generate the BCrypt password hash offline; never
   put the cleartext password in SQL, source control or logs.
2. Insert the administrator user and its profile using the normal schema and
   select the `ADMIN` role ID by its unique `label`. Use a transaction and
   verify that the username does not already exist before committing.
3. Record the change ID, operator and timestamp in the deployment audit trail.
   Do not copy local `data.sql` demo users or credentials into production.
4. Rotate the bootstrap credential through the normal password-management
   process after the first verified login.

Role creation and first-admin provisioning are deliberately separate so a
fresh production database receives only the role metadata required for normal
registration.
