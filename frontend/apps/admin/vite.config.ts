/// <reference types="vitest/config" />
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// Served under /admin behind Caddy; the API is on the same origin at /api.
export default defineConfig({
  base: "/admin/",
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": new URL("./src", import.meta.url).pathname } },
  server: { proxy: { "/api": process.env.API_URL ?? "http://localhost:8080" } },
  test: { include: ["src/**/*.test.ts"] },
});
