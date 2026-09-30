# Password recovery delivery and revocation

Unprofiled and production applications require a real SMTP adapter. The bounded
memory fixture is restricted to `local-h2` or `test`, and an explicitly active
`production` profile overrides either fixture profile.

Set the following deployment secrets/configuration through the existing secret
manager; never commit their values:

- `SAAS_PASSWORD_RESET_SMTP_HOST`
- `SAAS_PASSWORD_RESET_SMTP_PORT` (default 587)
- `SAAS_PASSWORD_RESET_SMTP_USERNAME`
- `SAAS_PASSWORD_RESET_SMTP_PASSWORD`
- `SAAS_PASSWORD_RESET_SMTP_FROM` (the provider-verified sender address)
- `SAAS_SECURITY_PASSWORD_RESET_FRONTEND_URL` (HTTPS origin plus `/reset-password`;
  no credentials, query or fragment)

The adapter requires authenticated STARTTLS and certificate/hostname validation.
Connection, read and write timeouts are five seconds. Missing/invalid settings
fail startup. No application option permits plaintext production SMTP. The
synthetic delivery test uses only a loopback SMTP fixture with inert addresses;
configuration tests separately require the production TLS/authentication policy.
This proves the implementation, not delivery by an owner's real mail provider.

Before enabling recovery for buyers, the owner must configure/verify the sender
with the mail provider, deploy the HTTPS frontend URL, and confirm inbox delivery
to an account they control. No real email was sent as part of these repairs.
Check spam handling and provider rejection policy. Delivery failures log only a
static warning, never the exception (which can contain the message/link).

Requests return the same 202/body for unknown, existing, throttled and SMTP-failed
accounts. A failed send rolls back replacement of the previous reset token; the
buyer is told to retry if no email arrives. This synchronous bounded adapter does
not promise queued background retries. Receipt by SMTP does not prove inbox
arrival. A rare application commit failure after SMTP acceptance can yield an
unusable link; requesting a new link recovers safely.

Links have a 15-minute lifetime. Their token appears only in the URL fragment and
redemption POST body. The screen consumes/removes the fragment with history
replacement and clears token/password state when destroyed. Notification string
representations are redacted. Do not enable JavaMail debug or log request bodies.

Redemption validates both passwords against registration's shared policy
(minimum eight characters; uppercase/lowercase/number; maximum 72 UTF-8 bytes),
then atomically consumes only an unexpired PASSWORD_RESET token. Password update
and deletion of all previous session tokens commit in the same transaction.
Wrong-purpose, expired, reused and concurrent losing requests cannot change it.
The rendered success path clears local authentication before returning to login.
The committed fixture proves old signed access/refresh credentials are denied
and a login with the new password creates a usable session for the same user.

## Companion release contracts

- CA6 retains its CPF/profile normalization and input bounds; deduplicate the
  shared password policy validation without losing the BCrypt byte limit.
- CA19/CA60 retain session-generation guards and configured-path credentials in
  the authentication service/interceptor. Recovery calls use only the configured
  directory endpoints.
- CA57 owns current-account enforcement and the product validation-cache
  propagation bound. Reset removes directory session rows immediately; a warmed
  product cache follows that documented bound. Never treat cache invalidation as
  instantaneous unless verified in the combined release.
- CA61 owns buyer order history. Re-login retains the same account/provider
  identity, so its owner-scoped pending-payment route remains the recovery path.
- CA33/CA34 supply shared form validation/accessibility. Keep recovery's rendered
  tests after their shared-field changes.
- CA52 must include any combined token/session schema requirements before
  production validation is enabled. This adapter introduces no new JPA table.

Findings remain OPEN pending independent review, merge and deployed verification.
