# Frontend container context

Build the frontend image with `frontend/` as the Docker context:

```bash
docker build -f frontend/Dockerfile -t natiart-frontend frontend
```

`frontend/.dockerignore` excludes host dependencies, generated output and local
configuration from that context. The repository root is the context for the
backend Dockerfiles, so the root `.dockerignore` protects their source copies.
Service-directory contexts do not contain the multi-project Gradle wrapper and
are not supported by those Dockerfiles.

The frontend builder installs from the committed lockfile with `npm ci` before
copying application source. Frontend CI runs `frontend/scripts/smoke_contexts.sh`
to compare clean and dirty image assets, inspect both effective contexts for
synthetic local files, and check that a mismatched manifest fails installation.
