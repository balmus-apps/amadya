import { TriangleAlertIcon } from "lucide-react";
import { lazy, Suspense } from "react";
import { useStore } from "zustand";
import { Empty, PageHeader } from "@/components/kit";
import { useT } from "@/lib/i18n";
import { session } from "@/lib/session";
import { AppLinks } from "./app-links";

// Swagger UI is about 1 MB: it loads only when this page is opened.
const ApiDocsViewer = lazy(() => import("./viewer"));

/**
 * Settings → Apps & API: the links to every app for admins and managers; below them, for admins only, the running
 * server's OpenAPI document with try-it-out using the admin's own session (managers never load Swagger).
 */
export function ApiDocsPage() {
  const t = useT();
  const roles = useStore(session, (s) => s.claims?.roles ?? []);
  const isAdmin = roles.includes("ADMIN");
  if (!isAdmin && !roles.includes("MANAGER")) return <Empty>{t("apiDocs.notAllowed")}</Empty>;
  return (
    <>
      <PageHeader title={t("apiDocs.title")} description={isAdmin ? t("apiDocs.description") : t("apiDocs.linksDescription")} />
      <AppLinks />
      {isAdmin && (
        <>
          <p className="mb-4 flex items-start gap-2 rounded-xl border border-destructive/40 bg-destructive/10 p-3 text-sm">
            <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-destructive" />
            {t("apiDocs.warning")}
          </p>
          <Suspense fallback={<Empty loading />}>
            <ApiDocsViewer />
          </Suspense>
        </>
      )}
    </>
  );
}
