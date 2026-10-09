# Backend container images

Build both service images from the repository root because the Gradle build is
multi-project and both service build scripts are configured together:

```bash
docker build -f backend/directory-service/Dockerfile -t natiart-directory .
docker build -f backend/product-service/Dockerfile -t natiart-product .
```

The root `.dockerignore` excludes host build outputs and local files from this
context. Each build stage copies both Gradle scripts but only the target
service's source. Gradle produces a fixed-name executable boot JAR; the runtime
stage copies that exact path as `/app/app.jar`.

Backend CI runs `backend/scripts/smoke_images.sh`. It builds clean and dirty
contexts, verifies that a stale host JAR is absent from the transferred context
and cannot change `/app/app.jar`, then starts each image with the local H2
profile and a dummy payment key. The smoke check performs no provider calls.

The root Compose stack adds `qa-h2` to `local-h2` and selects product's explicit
`qa` target. That target bundles the committed gallery and restores it into
`/tmp/natiart-qa-product-images` on every process boot. The entrypoint confines
reset to that exact disposable path and requires both QA/local profiles without
production. Default product builds end at the `production` target and include
neither gallery files nor storage reset configuration.

`QaStartup` resets each service's corresponding private provider fixture before
writing its readiness marker. The entrypoint clears that marker on every boot.
H2 schemas and `qa-data.sql` are recreated each time; there is no seed-once marker
or persistent QA database. See [the QA guide](../../qa/README.md) for fixed-fixture
editing and full-stack checks.
