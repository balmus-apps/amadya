import { createPromotion, deletePromotion, listProducts, listPromotions, updatePromotion, type Promotion, type PromotionRequest } from "@amadya/api-client";
import { Badge, Button, Card, CardContent, Input, NativeSelect, Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle, Switch } from "@amadya/ui";
import { PlusIcon } from "lucide-react";
import { useState } from "react";
import { ActiveBadge, emptyText, Field, ImageUpload, LocalizedInputs, PageHeader } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { formatDateTime, lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

const toLocalInput = (iso?: string) => (iso ? new Date(new Date(iso).getTime() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 16) : "");
const fromLocalInput = (v: string) => (v ? new Date(v).toISOString() : undefined);

export function PromotionsPage() {
  const t = useT();
  const { locale } = useLocale();
  const [editing, setEditing] = useState<Promotion | "new" | null>(null);
  const { data = [] } = useApi(["promotions"], () => listPromotions());
  return (
    <>
      <PageHeader
        title={t("promotions.title")}
        actions={
          <Button onClick={() => setEditing("new")}>
            <PlusIcon /> {t("promotions.new")}
          </Button>
        }
      />
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {data.map((p, i) => (
          <Card key={p.id} className="cursor-pointer overflow-hidden transition hover:shadow-md" onClick={() => setEditing(p)}>
            <div className={`relative flex min-h-32 flex-col justify-end p-5 ${i % 2 ? "bg-primary text-primary-foreground" : "bg-foreground text-background"}`}>
              {p.imageUrl && <img src={p.imageUrl} alt="" className="absolute inset-0 size-full object-cover opacity-40" />}
              {p.badge && <span className="absolute top-3 right-3 rounded-lg bg-accent px-2 py-1 text-sm font-extrabold text-accent-foreground">{p.badge}</span>}
              <p className="relative text-xl font-extrabold">{lt(p.title, locale)}</p>
              {p.subtitle && <p className="relative text-sm opacity-80">{lt(p.subtitle, locale)}</p>}
            </div>
            <CardContent className="flex flex-wrap items-center gap-2 p-3 text-xs">
              <ActiveBadge active={p.active} />
              {(p.startsAt || p.endsAt) && (
                <Badge variant="outline">
                  {p.startsAt ? formatDateTime(p.startsAt, locale) : "…"} → {p.endsAt ? formatDateTime(p.endsAt, locale) : "…"}
                </Badge>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
      {editing && <PromotionSheet promotion={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function PromotionSheet({ promotion, onClose }: { promotion?: Promotion; onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const { data: products = [] } = useApi(["products", ""], () => listProducts());
  const [form, setForm] = useState<PromotionRequest>({
    title: promotion?.title ?? emptyText(),
    subtitle: promotion?.subtitle ?? emptyText(),
    badge: promotion?.badge,
    imageUrl: promotion?.imageUrl,
    productId: promotion?.productId,
    startsAt: promotion?.startsAt,
    endsAt: promotion?.endsAt,
    sortOrder: promotion?.sortOrder ?? 0,
    active: promotion?.active ?? true,
  });
  const set = (patch: Partial<PromotionRequest>) => setForm((f) => ({ ...f, ...patch }));
  const save = useApiMutation((req: PromotionRequest) => (promotion ? updatePromotion({ path: { id: promotion.id }, body: req }) : createPromotion({ body: req })), {
    invalidate: [["promotions"]],
    success: t("common.saved"),
    onSuccess: onClose,
  });
  const remove = useApiMutation(() => deletePromotion({ path: { id: promotion!.id } }), { invalidate: [["promotions"]], onSuccess: onClose });
  return (
    <Sheet open onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="sm:max-w-xl">
        <SheetHeader>
          <SheetTitle>{promotion ? lt(promotion.title, locale) : t("promotions.new")}</SheetTitle>
        </SheetHeader>
        <div className="flex-1 space-y-4 overflow-y-auto px-5 pb-6">
          <LocalizedInputs value={form.title} onChange={(title) => set({ title })} labelRo={t("promotions.titleRo")} labelEn={t("promotions.titleEn")} />
          <LocalizedInputs value={form.subtitle ?? emptyText()} onChange={(subtitle) => set({ subtitle })} labelRo={t("promotions.subtitleRo")} labelEn={t("promotions.subtitleEn")} />
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label={t("promotions.badge")} hint={t("promotions.badgeHint")}>
              <Input value={form.badge ?? ""} maxLength={20} onChange={(e) => set({ badge: e.target.value })} />
            </Field>
            <Field label={t("promotions.product")}>
              <NativeSelect value={form.productId ?? ""} onChange={(e) => set({ productId: e.target.value || undefined })}>
                <option value="">{t("common.none")}</option>
                {products
                  .filter((p) => !p.archived)
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {lt(p.name, locale)}
                    </option>
                  ))}
              </NativeSelect>
            </Field>
            <Field label={t("promotions.startsAt")}>
              <Input type="datetime-local" value={toLocalInput(form.startsAt)} onChange={(e) => set({ startsAt: fromLocalInput(e.target.value) })} />
            </Field>
            <Field label={t("promotions.endsAt")}>
              <Input type="datetime-local" value={toLocalInput(form.endsAt)} onChange={(e) => set({ endsAt: fromLocalInput(e.target.value) })} />
            </Field>
          </div>
          <Field label={t("products.image")}>
            <ImageUpload value={form.imageUrl} onChange={(imageUrl) => set({ imageUrl })} />
          </Field>
          <div className="flex items-end gap-6">
            <Field label={t("promotions.sortOrder")} className="w-24">
              <Input type="number" value={form.sortOrder ?? 0} onChange={(e) => set({ sortOrder: Number(e.target.value) })} />
            </Field>
            <label className="flex items-center gap-2 pb-2 text-sm font-medium">
              <Switch checked={form.active} onCheckedChange={(active) => set({ active })} /> {t("common.active")}
            </label>
          </div>
        </div>
        <SheetFooter className="flex-row">
          {promotion && (
            <Button variant="outline" onClick={() => confirm(t("common.confirmDelete")) && remove.mutate(undefined)}>
              {t("common.delete")}
            </Button>
          )}
          <Button className="flex-1" disabled={!form.title.ro || save.isPending} onClick={() => save.mutate(form)}>
            {t("common.save")}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
