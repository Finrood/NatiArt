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
`MELHORENVIO_API_TOKEN`, `NATIART_PAYMENT_ASAAS_APIKEY`,
`NATIART_PAYMENT_ASAAS_PAYMENTS_URL`, and `CORS_ALLOWED_ORIGINS`; keep these
in the deployment secret store rather than source control.
