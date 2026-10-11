import { initStaffApi } from "@amadya/staff-auth";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./styles.css";

initStaffApi({ storageKey: "amadya-waiter-refresh" });

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
