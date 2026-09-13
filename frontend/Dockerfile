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

# Stage 2: serve the immutable release directly.
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/natiart-app/dist/nati-art-frontend/browser /usr/share/nginx/html

EXPOSE 80
