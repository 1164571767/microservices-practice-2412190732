FROM node:24-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,id=petcare-npm,sharing=locked,target=/root/.npm npm ci --prefer-offline --fetch-timeout=60000 --fetch-retries=1
COPY frontend/ ./
RUN npm run build
FROM nginx:1.28-alpine
COPY gateway/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend /frontend/dist /usr/share/nginx/html
EXPOSE 8080
