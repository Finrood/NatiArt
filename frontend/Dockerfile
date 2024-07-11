# Dockerfile for natiart-frontend
FROM node:22 AS build
WORKDIR /app/natiart-app
COPY natiart-app/package*.json ./
RUN npm install
COPY natiart-app .
# Build the application
ARG NODE_ENV=production
ARG BUILD_TIMESTAMP
RUN echo "NODE_ENV is set to $NODE_ENV"  # Print NODE_ENV
RUN npm run build -- --configuration=$NODE_ENV

# Create a script to copy files
RUN echo '#!/bin/sh\n\
if [ -d "/app/natiart-app/dist/nati-art-frontend/browser" ]; then\n\
  cp -r /app/natiart-app/dist/nati-art-frontend/browser/* /app/dist/\n\
  echo "Files copied successfully"\n\
else\n\
  echo "Source directory does not exist"\n\
fi\n\
exec "$@"' > /docker-entrypoint.sh && chmod +x /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["npm", "start"]
