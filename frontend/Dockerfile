# Stage 1: Build
FROM node:22.22.3 AS build
WORKDIR /app/natiart-app

# Copy only the necessary files for the build process
COPY natiart-app/package*.json ./
RUN npm ci

# Copy the entire application for building
COPY natiart-app .

# Build the application
ARG NODE_ENV=production
RUN npm run build -- --configuration=$NODE_ENV

# Stage 2: serve the immutable release directly.
FROM nginx:1.27-alpine AS runtime
ENV NATIART_PUBLIC_SCHEME=https

COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --chmod=755 docker/25-publish-assets.sh /docker-entrypoint.d/25-publish-assets.sh

VOLUME /var/lib/natiart-assets
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
COPY --from=build /app/natiart-app/dist/nati-art-frontend/browser /usr/share/nginx/html

EXPOSE 80

# Only the explicit QA target exposes the private fixture control page.
FROM runtime AS qa
ENV NATIART_QA_UPSTREAM=http://natiart-fixtures:8090
COPY docker/qa/locations.conf.template /etc/nginx/templates/qa/locations.conf.template

# Normal builds retain the production image without QA locations.
FROM runtime AS production
