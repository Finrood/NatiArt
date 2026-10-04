# Request tracing and log privacy

Both services accept a bounded `X-Request-ID` containing only ASCII letters,
digits, dots, underscores, and hyphens (1–64 characters). A missing or unsafe
value is replaced with a generated UUID. Each response includes the effective
ID and exposes the header to allowed browser origins. The ID is diagnostic
context, never proof of identity or permission.

When following a checkout, send the same ID to directory and product service.
The product service forwards it to payment calls. Registration writes the ID
with the durable Asaas provisioning job; the async wake-up and scheduled retries
restore it to the log context before provider work. Existing jobs without an ID
receive one when first claimed. Search `requestId` in application logs to trace
one operation; request URLs and provider response bodies are unnecessary.

Provider failure logs contain numeric HTTP status, a bounded response size,
and a fixed failure category. Never log upstream response bodies, exception
messages or stack traces from provider calls, credentials, payment details,
CPF, email, or customer address. Do not send provider failures or request IDs
to browser telemetry beyond the response header. Avoid placing personal data
in caller-supplied request IDs.

Before production rollout, configure the log collector to redact credentials,
payment data, CPF, email, and addresses as a second line of defense. Grant log
read access only to operators who need it for support or incident response;
audit access and exports. Retain application logs for at most 30 days unless a
documented legal hold requires longer. Remove temporary incident exports when
the investigation closes. Verify the effective collector policy during each
deployment because application code cannot enforce downstream retention.
