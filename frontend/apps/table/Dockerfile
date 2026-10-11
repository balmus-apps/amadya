# Build context: frontend/ (the pnpm workspace root). Served by Caddy at /t (the table QR).
# Static output: built natively even when the image targets another platform.
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
RUN npm install -g pnpm@10.34.6
WORKDIR /repo
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages packages
COPY apps/table apps/table
RUN pnpm install --frozen-lockfile --filter @amadya/table...
RUN pnpm --filter @amadya/table exec vite build

FROM nginx:1.29-alpine
COPY apps/table/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/apps/table/dist /usr/share/nginx/html
EXPOSE 80
