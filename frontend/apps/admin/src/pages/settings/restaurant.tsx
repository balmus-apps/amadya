import { getRestaurantSettings, updateRestaurantSettings, type Features, type Locale, type RestaurantSettings } from "@amadya/api-client";
import { resolveTheme } from "@amadya/theme";
import { Button, Card, CardContent, CardHeader, CardTitle, Checkbox, Input, NativeSelect, Switch } from "@amadya/ui";
import { useEffect, useState } from "react";
import { Field, ImageUpload, PageHeader } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { useT } from "@/lib/i18n";

const colours = ["primary", "secondary", "accent", "background", "foreground"] as const;

export function RestaurantSettingsPage() {
  const t = useT();
  const { data } = useApi(["restaurant-settings"], () => getRestaurantSettings());
  const [form, setForm] = useState<RestaurantSettings | null>(null);
  useEffect(() => {
    if (data) setForm(data);
  }, [data]);
  const save = useApiMutation((req: RestaurantSettings) => updateRestaurantSettings({ body: req }), { invalidate: [["restaurant-settings"]], success: t("common.saved") });
  if (!form) return null;
  const set = (patch: Partial<RestaurantSettings>) => setForm({ ...form, ...patch });
  const theme = resolveTheme(form.theme);
  const text = (key: keyof RestaurantSettings, label: string) => (
    <Field label={label}>
      <Input value={(form[key] as string | undefined) ?? ""} onChange={(e) => set({ [key]: e.target.value })} />
    </Field>
  );
  const hours = (day: number) => form.openingHours.find((h) => h.dayOfWeek === day);

  return (
    <>
      <PageHeader
        title={t("settings.restaurantTitle")}
        actions={
          <Button disabled={save.isPending} onClick={() => save.mutate(form)}>
            {t("common.save")}
          </Button>
        }
      />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t("settings.profile")}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-2">
            {text("name", t("settings.name"))}
            {text("legalName", t("settings.legalName"))}
            {text("cui", t("procurement.cui"))}
            {text("regCom", t("procurement.regCom"))}
            {text("phone", t("procurement.phone"))}
            {text("email", t("procurement.email"))}
            <div className="sm:col-span-2">{text("address", t("procurement.address"))}</div>
            {text("currency", t("settings.currency"))}
            {text("orderNumberPrefix", t("settings.orderPrefix"))}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("settings.branding")}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Field label={t("settings.logo")}>
              <ImageUpload value={form.logoUrl} onChange={(logoUrl) => set({ logoUrl })} />
            </Field>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {colours.map((c) => (
                <Field key={c} label={t(`settings.${c}`)}>
                  <div className="flex items-center gap-2">
                    <input type="color" className="h-10 w-12 cursor-pointer rounded border" value={theme[c]} onChange={(e) => set({ theme: { ...form.theme, [c]: e.target.value.toUpperCase() } })} />
                    <span className="font-mono text-xs">{theme[c]}</span>
                  </div>
                </Field>
              ))}
              <Field label={t("settings.radius")}>
                <Input value={form.theme.radius ?? theme.radius} onChange={(e) => set({ theme: { ...form.theme, radius: e.target.value } })} />
              </Field>
            </div>
            <div className="rounded-xl p-4" style={{ background: theme.background, color: theme.foreground, borderRadius: theme.radius }}>
              <p className="font-bold">{form.name}</p>
              <span className="mt-2 inline-block px-4 py-2 font-semibold" style={{ background: theme.primary, color: theme.onPrimary, borderRadius: theme.radius }}>
                Comandă
              </span>
              <span className="ml-2 inline-block rounded px-2 py-1 text-sm font-bold" style={{ background: theme.accent, color: "#fff" }}>
                20 LEI
              </span>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("settings.features")}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-2">
            {(Object.keys(form.features) as (keyof Features)[]).map((k) => (
              <label key={k} className="flex items-center gap-2 text-sm font-medium">
                <Switch checked={form.features[k]} onCheckedChange={(v) => set({ features: { ...form.features, [k]: v } })} /> {t(`settings.featureNames.${k}`)}
              </label>
            ))}
            <Field label={t("settings.locales")}>
              <div className="flex gap-4">
                {(["ro", "en"] as Locale[]).map((l) => (
                  <label key={l} className="flex items-center gap-2 text-sm uppercase">
                    <Checkbox
                      checked={form.locales.includes(l)}
                      onCheckedChange={(c) => set({ locales: c ? [...form.locales, l] : form.locales.filter((x) => x !== l) })}
                    />
                    {l}
                  </label>
                ))}
              </div>
            </Field>
            <Field label={t("settings.defaultLocale")}>
              <NativeSelect value={form.defaultLocale} onChange={(e) => set({ defaultLocale: e.target.value as Locale })}>
                {form.locales.map((l) => (
                  <option key={l} value={l}>
                    {l.toUpperCase()}
                  </option>
                ))}
              </NativeSelect>
            </Field>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("settings.openingHours")}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {[1, 2, 3, 4, 5, 6, 7].map((day) => {
              const h = hours(day);
              const update = (patch: { opens?: string; closes?: string } | null) =>
                set({
                  openingHours: patch === null ? form.openingHours.filter((x) => x.dayOfWeek !== day) : [...form.openingHours.filter((x) => x.dayOfWeek !== day), { dayOfWeek: day, opens: "10:00", closes: "22:00", ...h, ...patch }],
                });
              return (
                <div key={day} className="grid grid-cols-[7rem_auto_1fr_1fr] items-center gap-2 text-sm">
                  <span className="font-medium">{t(`settings.days.${day}`)}</span>
                  <Switch checked={!!h} onCheckedChange={(open) => update(open ? {} : null)} />
                  {h ? (
                    <>
                      <Input type="time" value={h.opens} onChange={(e) => update({ opens: e.target.value })} aria-label={t("settings.opens")} />
                      <Input type="time" value={h.closes} onChange={(e) => update({ closes: e.target.value })} aria-label={t("settings.closes")} />
                    </>
                  ) : (
                    <span className="col-span-2 text-muted-foreground">{t("settings.closed")}</span>
                  )}
                </div>
              );
            })}
          </CardContent>
        </Card>
      </div>
    </>
  );
}
