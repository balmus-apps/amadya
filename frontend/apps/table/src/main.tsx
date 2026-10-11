import { configureApi } from "@amadya/api-client";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App, currentLocale } from "./App";
import "./styles.css";

// Guests have no login: their device token is sent per request (X-Table-Guest).
configureApi({ baseUrl: "/api/v1", getLocale: currentLocale });

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
