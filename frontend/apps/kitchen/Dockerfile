# Build context: frontend/ (the pnpm workspace root). Served by Caddy at /kitchen.
# Static output: built natively even when the image targets another platform.
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
RUN npm install -g pnpm@10.34.6
WORKDIR /repo
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages packages
COPY apps/kitchen apps/kitchen
RUN pnpm install --frozen-lockfile --filter @amadya/kitchen...
RUN pnpm --filter @amadya/kitchen exec vite build

FROM nginx:1.29-alpine
COPY apps/kitchen/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/apps/kitchen/dist /usr/share/nginx/html
EXPOSE 80
