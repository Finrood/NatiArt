# Stage 1: Build
FROM node:22 AS build
WORKDIR /app/natiart-app

# Copy only the necessary files for the build process
COPY natiart-app/package*.json ./
RUN npm install

# Copy the entire application for building
COPY natiart-app .

# Build the application
ARG NODE_ENV=production
RUN npm run build -- --configuration=$NODE_ENV

# Stage 2: Final (minimal runtime to populate volume)
FROM alpine:3.20

WORKDIR /app

# Copy built assets
COPY --from=build /app/natiart-app/dist/nati-art-frontend/browser /app/dist

# Use a non-root user (uid/gid 1000 for consistency)
RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup

USER appuser

# Keep container running to populate the volume
CMD ["tail", "-f", "/dev/null"]
