# Build context: frontend/ (the pnpm workspace root). Served by Caddy at /cashier.
# Static output: built natively even when the image targets another platform.
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
RUN npm install -g pnpm@10.34.6
WORKDIR /repo
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages packages
COPY apps/cashier apps/cashier
RUN pnpm install --frozen-lockfile --filter @amadya/cashier...
RUN pnpm --filter @amadya/cashier exec vite build

FROM nginx:1.29-alpine
COPY apps/cashier/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/apps/cashier/dist /usr/share/nginx/html
EXPOSE 80
