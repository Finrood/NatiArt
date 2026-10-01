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
