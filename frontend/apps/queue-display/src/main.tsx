import { configureApi } from "@amadya/api-client";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./styles.css";

// Public screen: no login. Names (promotions) come in the restaurant's main language unless ?lang= says otherwise.
configureApi({ baseUrl: "/api/v1", getLocale: () => new URLSearchParams(window.location.search).get("lang") ?? "ro" });

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
