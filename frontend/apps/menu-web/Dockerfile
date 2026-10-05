# Build context: frontend/ (the pnpm workspace root).
# Built on the target platform: the standalone output ships native modules (sharp).
FROM node:24-alpine AS build
RUN npm install -g pnpm@10.34.6
WORKDIR /repo
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages packages
COPY apps/menu-web apps/menu-web
RUN pnpm install --frozen-lockfile --filter @amadya/menu-web...
RUN pnpm --filter @amadya/menu-web build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0 NEXT_TELEMETRY_DISABLED=1
COPY --from=build --chown=node:node /repo/apps/menu-web/.next/standalone ./
COPY --from=build --chown=node:node /repo/apps/menu-web/.next/static ./apps/menu-web/.next/static
COPY --from=build --chown=node:node /repo/apps/menu-web/public ./apps/menu-web/public
USER node
EXPOSE 3000
CMD ["node", "apps/menu-web/server.js"]
