# Customer account management

`PUT /users/current/profile` takes `{profile, currentPassword}`. The current
JWT principal determines the user; submitted profile IDs never select a row.
`profile.version` must match the saved JPA version, otherwise return
`409 PROFILE_CONFLICT`. A successful response includes the same profile ID and
its new version. Names, CPF, optional phone, country, UF, city, neighborhood,
CEP, street, house number and optional apartment/complement share registration
validation. CPF/CEP/phone normalize to digits, UF to uppercase, and textual
fields trim. Username/email and roles are not editable through this endpoint.

House number is a required nonblank string, up to 255 characters, for new
registrations and profile saves. Apartment/suite is the existing optional
`complement` field. The database column stays nullable for existing accounts:
never invent a house number or copy an apartment value into it. Existing users
can sign in and read history; editing details or completing a new checkout asks
for the missing number. Order delivery snapshots remain unchanged.

The account and provisioning job rows serialize edits. Signup-triggered
provisioning locks the account before reading its profile and claiming the job,
so a claim waiting for an edit uses the newly committed address. Successful
provisioning follows the same user-then-job lock order as account updates,
avoiding a reversed lock order when inserting the provider mapping. An `IN_PROGRESS` job
returns `409 ACCOUNT_SETUP_IN_PROGRESS` to prevent saving a different profile
while its earlier provider request is in flight. Corrected profiles reset
`PENDING`/`FAILED` jobs to retry now; a missing job is created durably. With an
existing Asaas mapping, update the *same* customer using a narrow PUT request
(name, CPF, email, phone, street/number/complement, neighborhood, CEP and stable
externalReference). Notification settings and existing charges are not changed.
Provider identity and `deleted=false` must be confirmed before the local copy is
saved. Provider rejection/timeout returns `503 PROVIDER_UNAVAILABLE` and retains
the old saved profile. The client preserves its draft for retry. Provider I/O is
bounded by a 5-second connect and 15-second read timeout while this user's rows
are locked. Other users do not share these account locks.

This is not a distributed transaction: a timeout can occur after Asaas applied
an update, and a database failure can occur after provider confirmation. Retry
the same customer ID with the desired values, never create a replacement. An
operator must reconcile an ambiguous provider/local outcome. Confirm the real
provider contract in staging, including clearing optional fields.

`POST /users/current/change-password` takes
`{currentPassword,password,passwordConfirmation}` and returns 204. Current
password verification is required for both account operations. Wrong current
password returns `400 CURRENT_PASSWORD_INCORRECT`, preserving the valid browser
session. Both operations share five attempts per account per minute; counters
commit before the account transaction even when an edit fails. Account HTTP
operations do not hold a database connection while acquiring a second connection
for this counter; the complete HTTP contract is tested with a one-connection
pool. Concurrent first attempts retry a unique-key collision in a fresh counter
transaction. Further attempts
return `429 TRY_LATER`. Password policy and matching confirmation are checked
before hashing. Responses and logs never include entered passwords.

Password changes lock the same user row as login, refresh and password reset,
replace the hash and delete *all* stored tokens atomically. Concurrent old-password
login/refresh cannot issue a surviving session after this change. Password reset
resolves a scalar token owner before taking the user lock, then consumes the
still-valid token atomically; this avoids loading a stale User version before
waiting. The browser clears credentials only after a confirmed password change.
After commit, `AccountStateChangedEvent` invalidates the product authentication
cache. If delivery fails, cached product validations expire within the configured
TTL (default 30 seconds); directory token revocation is immediate. Production
must configure matching invalidation secrets and monitor failures.

## Rollout

Back up and rehearse against a production-shaped PostgreSQL copy. The additive
schema change is:

```sql
ALTER TABLE profile ADD COLUMN IF NOT EXISTS house_number varchar(255);
```

`profile.version` already exists; this change only exposes it to clients. Keep
nullable legacy rows and review incomplete addresses through the account UI.
Coordinate backend/frontend releases: older already-open registration forms
have no house-number field and must reload the new storefront before submitting.
The new frontend must not be served against an older backend that drops that
field or lacks account endpoints. The existing `ddl-auto=update` deployment
policy is unchanged; this does not introduce an incomplete migration framework.
Keep backups and rehearse application rollback with the additive column retained.

Contract references: [Asaas customer update](https://docs.asaas.com/reference/update-existing-customer)
and [OWASP reauthentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).
