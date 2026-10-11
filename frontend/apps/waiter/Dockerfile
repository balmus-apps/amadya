# Build context: frontend/ (the pnpm workspace root). Served by Caddy at /waiter.
# Static output: built natively even when the image targets another platform.
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
RUN npm install -g pnpm@10.34.6
WORKDIR /repo
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages packages
COPY apps/waiter apps/waiter
RUN pnpm install --frozen-lockfile --filter @amadya/waiter...
RUN pnpm --filter @amadya/waiter exec vite build

FROM nginx:1.29-alpine
COPY apps/waiter/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/apps/waiter/dist /usr/share/nginx/html
EXPOSE 80
