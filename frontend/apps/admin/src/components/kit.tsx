import { client, type LocalizedText, type UploadedFile } from "@amadya/api-client";
import { Badge, Button, cn, Input, Label, toast } from "@amadya/ui";
import { ImageUpIcon, Loader2Icon } from "lucide-react";
import { useRef, useState, type ReactNode } from "react";
import { useT } from "@/lib/i18n";
import { accessToken } from "@/lib/session";

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight">{title}</h1>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Field({ label, hint, htmlFor, children, className }: { label: string; hint?: string; htmlFor?: string; children: ReactNode; className?: string }) {
  return (
    <div className={cn("space-y-1.5", className)}>
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

/** Romanian is required, English optional — edited side by side. */
export function LocalizedInputs({
  value,
  onChange,
  labelRo,
  labelEn,
  multiline,
}: {
  value: LocalizedText;
  onChange: (v: LocalizedText) => void;
  labelRo: string;
  labelEn: string;
  multiline?: boolean;
}) {
  const Comp = multiline ? "textarea" : "input";
  const cls = "flex w-full rounded-lg border bg-card px-3 py-2 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/40";
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Field label={labelRo}>
        <Comp className={cn(cls, multiline ? "min-h-20" : "h-10")} value={value.ro} required onChange={(e) => onChange({ ...value, ro: e.target.value })} />
      </Field>
      <Field label={labelEn}>
        <Comp className={cn(cls, multiline ? "min-h-20" : "h-10")} value={value.en ?? ""} onChange={(e) => onChange({ ...value, en: e.target.value })} />
      </Field>
    </div>
  );
}

export function emptyText(): LocalizedText {
  return { ro: "", en: "" };
}

/** Uploads to POST /admin/files and returns the public URL. */
export function ImageUpload({ value, onChange }: { value?: string; onChange: (url: string | undefined) => void }) {
  const t = useT();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  async function upload(file: File) {
    setBusy(true);
    try {
      const body = new FormData();
      body.append("file", file);
      const res = await fetch(`${client.getConfig().baseUrl}/admin/files`, { method: "POST", body, headers: { Authorization: `Bearer ${await accessToken()}` } });
      const json = await res.json();
      if (!res.ok) throw new Error(json.detail ?? res.statusText);
      onChange((json as UploadedFile).url);
    } catch (e) {
      toast.error(String((e as Error).message));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex items-center gap-3">
      <div className="flex size-20 shrink-0 items-center justify-center overflow-hidden rounded-xl border bg-muted">
        {value ? <img src={value} alt="" className="size-full object-cover" /> : <ImageUpIcon className="size-6 text-muted-foreground" />}
      </div>
      <div className="flex flex-col gap-1.5">
        <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
        <Button type="button" variant="outline" size="sm" onClick={() => input.current?.click()} disabled={busy}>
          {busy && <Loader2Icon className="animate-spin" />} {t("products.upload")}
        </Button>
        {value && (
          <Button type="button" variant="ghost" size="sm" onClick={() => onChange(undefined)}>
            {t("common.remove")}
          </Button>
        )}
      </div>
    </div>
  );
}

export function ActiveBadge({ active }: { active: boolean }) {
  const t = useT();
  return <Badge variant={active ? "success" : "muted"}>{active ? t("common.active") : t("common.inactive")}</Badge>;
}

/** Empty state that shows a skeleton while the first load is still running. */
export function Empty({ children, loading }: { children?: ReactNode; loading?: boolean }) {
  const t = useT();
  if (loading) return <div className="h-32 animate-pulse rounded-xl bg-muted" aria-busy="true" />;
  return <p className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">{children ?? t("common.empty")}</p>;
}

export function NumberInput(props: React.ComponentProps<typeof Input>) {
  return <Input inputMode="decimal" {...props} />;
}

/** Comma-separated text <-> string array. */
export function splitList(value: string) {
  return value
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}
