import type { NextConfig } from "next";
import path from "node:path";
import createNextIntlPlugin from "next-intl/plugin";

const apiInternalUrl = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

const config: NextConfig = {
  output: "standalone",
  // Trace workspace packages from the monorepo root so the standalone server includes them.
  outputFileTracingRoot: path.join(import.meta.dirname, "../.."),
  transpilePackages: ["@amadya/ui", "@amadya/api-client", "@amadya/i18n", "@amadya/theme", "@amadya/customer-core"],
  poweredByHeader: false,
  // Same-origin API in development; in Docker, Caddy routes /api to the Core API before Next.js sees it.
  async rewrites() {
    return [{ source: "/api/v1/:path*", destination: `${apiInternalUrl}/api/v1/:path*` }];
  },
};

export default createNextIntlPlugin("./src/i18n/request.ts")(config);
