# NatiArt local setup

Use Java 25 and Node.js 22.22.3 (or a later supported Angular 22 runtime).
From the repository root, run `CHROME_BIN=/usr/bin/google-chrome ./gradlew buildAll`
to build and test both backend services and the Angular storefront. Use the path
to your installed Chrome or Chromium for `CHROME_BIN`. The root task runs both
service `build` tasks, `npm ci`, the production Angular build, and Karma tests.
Backend services use the local H2 profile
for development and listen on ports 8081 (directory) and 8082 (product); the
storefront runs on port 4200. Start the directory service before the product
service, then run `npm start` in `frontend/natiart-app`.

Production requires `DATASOURCE_URL`, `DATASOURCE_USERNAME`,
`DATASOURCE_PASSWORD`, `DIRECTORY_SERVICE_URL`, `MELHORENVIO_API_URL`,
`MELHORENVIO_API_TOKEN`, `MELHORENVIO_FROM_POSTAL_CODE`,
`MELHORENVIO_USER_AGENT` (application name and a real technical contact email),
`NATIART_PAYMENT_ASAAS_APIKEY`,
`NATIART_PAYMENT_ASAAS_PAYMENTS_URL`, and `CORS_ALLOWED_ORIGINS`; keep these
in the deployment secret store rather than source control.

The production verifier checks both locale bundles against the directory/product
URLs configured in `environment.production.ts`, including same-origin
`/server/directory` and `/server/product` routed through nginx. It rejects
missing locales, development endpoints and source maps. Artifact regression tests
copy the actual production output and deliberately break each boundary; they run
as part of `buildAll` and frontend CI.

Address and freight service choices, production setup and dispatch verification
are documented in [the integration audit](backend/product-service/docs/address-shipping-audit.md).
