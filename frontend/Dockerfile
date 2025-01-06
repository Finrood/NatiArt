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
ARG BUILD_TIMESTAMP
RUN echo "NODE_ENV is set to $NODE_ENV"  # Print NODE_ENV
RUN npm run build -- --configuration=$NODE_ENV

# Stage 2: Clean up and prepare for volume mount (no need to install nginx)
FROM node:22 AS cleanup

WORKDIR /app/natiart-app

# Copy only the necessary built artifacts (dist folder)
COPY --from=build /app/natiart-app/dist/nati-art-frontend/browser /app/dist

# Set the entrypoint to ensure it doesn't start a server (as Nginx will serve the files)
CMD ["sh", "-c", "while :; do sleep 2073600; done"]
