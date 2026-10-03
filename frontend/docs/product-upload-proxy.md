# Product upload proxy limits

The product API route accepts at most 101 MiB at nginx. The backend continues to
apply its 100 MiB total multipart body limit, 10 MiB per-file limit, image-count
and image-content validation. The extra 1 MiB prevents the proxy from imposing a
smaller envelope on accepted multipart uploads; it does not raise backend limits.
Other routes retain nginx's explicit 1 MiB limit. Authentication, cookies, query
strings, multipart fields and backend validation responses pass through unchanged.

The real update/rollback container fixture posts a 2 MiB multipart image part to
`/server/product/products/create`, checks the exact forwarded body/credentials/query,
and rejects declared requests above each route limit before sending large bodies.
Its upstream is an inert HTTP fixture, not a production product write.
