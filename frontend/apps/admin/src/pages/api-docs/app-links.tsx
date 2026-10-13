import { getRestaurantSettings } from "@amadya/api-client";
import { Button, Card, CardContent, CardHeader, CardTitle, toast } from "@amadya/ui";
import { CopyIcon, ExternalLinkIcon } from "lucide-react";
import { useApi } from "@/lib/data";
import { useT } from "@/lib/i18n";

/** Every app of this install and the path Caddy serves it at (infra/caddy/Caddyfile). */
const APPS = [
  { id: "menu", path: "/" },
  { id: "admin", path: "/admin/" },
  { id: "waiter", path: "/waiter/" },
  { id: "cashier", path: "/cashier/" },
  { id: "kitchen", path: "/kitchen/" },
  { id: "queue", path: "/queue/" },
] as const;

/**
 * The addresses of all the apps, one click away. The host is the install's public address from the server
 * (AMADYA_PUBLIC_URL, set from AMADYA_DOMAIN in .env), or this page's own address when the server does not say.
 */
export function AppLinks() {
  const t = useT();
  const { data: settings } = useApi(["settings"], () => getRestaurantSettings());
  const base = (settings?.publicUrl || window.location.origin).replace(/\/+$/, "");

  async function copy(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      toast.success(t("apiDocs.links.copied"));
    } catch {
      toast.error(url);
    }
  }

  return (
    <Card className="mb-4">
      <CardHeader>
        <CardTitle>{t("apiDocs.links.title")}</CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="divide-y">
          {APPS.map(({ id, path }) => {
            const url = base + path;
            return (
              <li key={id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2">
                <div className="min-w-48 flex-1">
                  <p className="font-semibold">{t(`apiDocs.links.apps.${id}`)}</p>
                  <a href={url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-sm break-all text-primary hover:underline">
                    {url.replace(/^https?:\/\//, "")} <ExternalLinkIcon className="size-3.5 shrink-0" />
                  </a>
                </div>
                <Button variant="ghost" size="icon" aria-label={t("apiDocs.links.copy")} onClick={() => copy(url)}>
                  <CopyIcon />
                </Button>
              </li>
            );
          })}
        </ul>
        <p className="mt-2 text-xs text-muted-foreground">{t("apiDocs.links.tableHint", { url: `${base.replace(/^https?:\/\//, "")}/t/…` })}</p>
      </CardContent>
    </Card>
  );
}
