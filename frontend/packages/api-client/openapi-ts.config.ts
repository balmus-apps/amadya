import { defineConfig } from "@hey-api/openapi-ts";

// Contract-first (ADR 0002): regenerate with `pnpm gen:api` after changing api/openapi/openapi.yaml.
export default defineConfig({
  input: "../../../api/openapi/openapi.yaml",
  output: { path: "src/generated", postProcess: ["prettier"] },
  plugins: [
    "@hey-api/client-fetch",
    "@hey-api/typescript",
    { name: "@hey-api/sdk", asClass: false },
    "@tanstack/react-query",
  ],
});
