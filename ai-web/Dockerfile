# syntax=docker/dockerfile:1.7
# 构建上下文必须是仓库根目录：docker compose build
FROM node:22-alpine AS build

RUN apk add --no-cache python3 make g++ \
    && npm install -g pnpm@12.3.4

WORKDIR /app

COPY ai-web/package.json ai-web/pnpm-lock.yaml ai-web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY ai-web ./
RUN pnpm build

FROM nginx:1.27-alpine

COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=6 \
    CMD wget -q -O - http://localhost/health | grep -q ok || exit 1
