
FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

ARG VITE_MAPBOX_TOKEN
RUN test -n "$VITE_MAPBOX_TOKEN"

# Vite schreibt VITE_* beim Build ins JS; Build-Args überschreiben die Werte aus .env
ARG VITE_KEYCLOAK_URL
ARG VITE_API_URL
ARG VITE_LOCATION_SERVICE_URL
ARG VITE_ENGINE_URL
RUN npm run build

FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
