"use client";

import { createOrder, unwrap, type ApiProblem } from "@amadya/api-client";
import { formatMoney, normalizePhone, type AppLocale } from "@amadya/i18n";
import { Button, Card, CardContent, cn, Input, Label, RadioGroup, RadioGroupItem, Separator, Textarea, toast } from "@amadya/ui";
import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeftIcon, ShieldCheckIcon } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Link, useRouter } from "@/i18n/navigation";
import { cartTotals, fromCents, lineCents, useCart } from "@/lib/cart";
import { useRecentOrders, useSavedContact } from "@/lib/recent-orders";
import { useHydrated } from "../header";
import { useSettings } from "../providers";

/** Pickup slots every 15 minutes, starting 30 minutes from now, until 3 hours ahead. */
function pickupSlots(now = new Date()): string[] {
  const start = new Date(now.getTime() + 30 * 60_000);
  start.setMinutes(Math.ceil(start.getMinutes() / 15) * 15, 0, 0);
  return Array.from({ length: 11 }, (_, i) => {
    const d = new Date(start.getTime() + i * 15 * 60_000);
    return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  }).filter((slot, i, all) => all.indexOf(slot) === i);
}

function toPickupIso(time: string): string {
  const [h, m] = time.split(":").map(Number);
  const d = new Date();
  d.setHours(h!, m!, 0, 0);
  if (d.getTime() < Date.now()) d.setDate(d.getDate() + 1);
  return d.toISOString();
}

export function CheckoutScreen() {
  const t = useTranslations("checkout");
  const tc = useTranslations("cart");
  const tCommon = useTranslations("common");
  const locale = useLocale() as AppLocale;
  const router = useRouter();
  const settings = useSettings();
  const hydrated = useHydrated();
  const items = useCart((s) => s.items);
  const clear = useCart((s) => s.clear);
  const remember = useRecentOrders((s) => s.remember);
  const { contact, save } = useSavedContact();
  const { totalCents, currency } = cartTotals(items);
  const slots = useMemo(() => pickupSlots(), []);
  const [submitting, setSubmitting] = useState(false);

  const schema = useMemo(
    () =>
      z
        .object({
          name: z.string().trim().min(2, t("invalidName")).max(80),
          phone: z.string().refine((v) => normalizePhone(v) !== null, t("invalidPhone")),
          email: z.union([z.literal(""), z.email(t("invalidEmail"))]),
          pickup: z.enum(["asap", "scheduled"]),
          pickupTime: z.string(),
          notes: z.string().max(500),
        }),
    [t],
  );
  type FormValues = z.infer<typeof schema>;

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: "", phone: "", email: "", pickup: "asap", pickupTime: slots[0] ?? "", notes: "" },
  });

  useEffect(() => {
    if (contact) form.reset({ ...form.getValues(), name: contact.name, phone: contact.phone, email: contact.email ?? "" });
  }, [contact, form]);

  useEffect(() => {
    if (hydrated && items.length === 0 && !submitting) router.replace("/");
  }, [hydrated, items.length, router, submitting]);

  async function onSubmit(values: FormValues) {
    setSubmitting(true);
    const phone = normalizePhone(values.phone)!;
    try {
      const order = await unwrap(
        createOrder({
          headers: { "Accept-Language": locale },
          body: {
            channel: "TAKEAWAY",
            customer: { name: values.name.trim(), phone, email: values.email || undefined },
            pickupAt: values.pickup === "scheduled" ? toPickupIso(values.pickupTime) : undefined,
            notes: values.notes.trim() || undefined,
            lines: items.map((i) => ({
              productId: i.productId,
              quantity: i.quantity,
              modifierOptionIds: i.options.map((o) => o.id),
              notes: i.notes,
            })),
          },
        }),
      );
      save({ name: values.name.trim(), phone, email: values.email || undefined });
      remember({ id: order.id, number: order.number, token: order.trackingToken!, createdAt: order.createdAt, total: order.total });
      router.push(`/order/${order.id}?t=${encodeURIComponent(order.trackingToken!)}`);
      clear();
    } catch (error) {
      const problem = (error as { problem?: ApiProblem }).problem;
      toast.error(problem?.detail ?? String(error));
      setSubmitting(false);
    }
  }

  if (!hydrated) return null;
  const errors = form.formState.errors;
  const pickupMode = form.watch("pickup");
  const total = formatMoney({ amount: fromCents(totalCents), currency }, locale);

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="mx-auto max-w-xl py-6" noValidate>
      <Link href="/" className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-muted-foreground hover:text-foreground">
        <ArrowLeftIcon className="size-4" /> {tCommon("back")}
      </Link>
      <h1 className="mb-6 text-3xl font-extrabold">{t("title")}</h1>

      <Card className="mb-4">
        <CardContent className="space-y-4">
          <h2 className="text-lg font-bold">{t("contact")}</h2>
          <Field label={t("name")} error={errors.name?.message} htmlFor="name">
            <Input id="name" autoComplete="name" aria-invalid={!!errors.name} {...form.register("name")} />
          </Field>
          <Field label={t("phone")} hint={t("phoneHint")} error={errors.phone?.message} htmlFor="phone">
            <Input id="phone" type="tel" inputMode="tel" autoComplete="tel" placeholder="07xx xxx xxx" aria-invalid={!!errors.phone} {...form.register("phone")} />
          </Field>
          <Field label={t("email")} error={errors.email?.message} htmlFor="email">
            <Input id="email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...form.register("email")} />
          </Field>
        </CardContent>
      </Card>

      <Card className="mb-4">
        <CardContent className="space-y-4">
          <h2 className="text-lg font-bold">{t("pickup")}</h2>
          <RadioGroup value={pickupMode} onValueChange={(v) => form.setValue("pickup", v as "asap" | "scheduled")} className="grid-cols-2">
            {(["asap", "scheduled"] as const).map((mode) => (
              <label
                key={mode}
                htmlFor={`pickup-${mode}`}
                className="flex cursor-pointer items-center gap-3 rounded-xl border bg-card p-3 has-[[data-state=checked]]:border-primary has-[[data-state=checked]]:bg-primary/5"
              >
                <RadioGroupItem id={`pickup-${mode}`} value={mode} />
                <span className="text-sm font-semibold">{t(mode)}</span>
              </label>
            ))}
          </RadioGroup>
          {pickupMode === "scheduled" && (
            <div className="flex flex-wrap gap-2" role="radiogroup" aria-label={t("pickupTime")}>
              {slots.map((slot) => (
                <button
                  key={slot}
                  type="button"
                  role="radio"
                  aria-checked={form.watch("pickupTime") === slot}
                  onClick={() => form.setValue("pickupTime", slot)}
                  className={cn(
                    "rounded-full border px-3 py-1.5 text-sm font-semibold tabular-nums",
                    form.watch("pickupTime") === slot ? "border-primary bg-primary text-primary-foreground" : "bg-card",
                  )}
                >
                  {slot}
                </button>
              ))}
            </div>
          )}
          <Field label={t("notes")} htmlFor="notes">
            <Textarea id="notes" rows={2} maxLength={500} {...form.register("notes")} />
          </Field>
        </CardContent>
      </Card>

      <Card className="mb-6">
        <CardContent>
          <h2 className="mb-3 text-lg font-bold">{t("summary")}</h2>
          <ul className="space-y-2 text-sm">
            {items.map((i) => (
              <li key={i.key} className="flex justify-between gap-4">
                <span>
                  <span className="font-semibold">{i.quantity} ×</span> {i.name}
                  {i.options.length > 0 && <span className="text-muted-foreground"> · {i.options.map((o) => o.name).join(", ")}</span>}
                </span>
                <span className="shrink-0 tabular-nums">{formatMoney({ amount: fromCents(lineCents(i)), currency: i.currency }, locale)}</span>
              </li>
            ))}
          </ul>
          <Separator className="my-3" />
          <div className="flex items-baseline justify-between">
            <span className="font-bold">{tc("total")}</span>
            <span className="text-xl font-extrabold">{total}</span>
          </div>
          <p className="text-right text-xs text-muted-foreground">{t("vatIncluded")}</p>
        </CardContent>
      </Card>

      <Button type="submit" size="lg" className="h-14 w-full text-base" disabled={submitting || !settings.features.takeaway}>
        {submitting ? t("placing") : t("placeOrder", { total })}
      </Button>
      <p className="mt-3 flex items-center justify-center gap-1.5 text-center text-xs text-muted-foreground">
        <ShieldCheckIcon className="size-3.5" /> {t("privacy")}
      </p>
    </form>
  );
}

function Field({ label, hint, error, htmlFor, children }: { label: string; hint?: string; error?: string; htmlFor: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {error ? <p className="text-sm text-destructive">{error}</p> : hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  );
}
