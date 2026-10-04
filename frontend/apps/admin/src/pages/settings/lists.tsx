import {
  createStation,
  createUser,
  createVatRate,
  listStations,
  listUsers,
  listVatRates,
  updateStation,
  updateUser,
  updateVatRate,
  type Locale,
  type Role,
  type Station,
  type StationRequest,
  type User,
  type VatRate,
  type VatRateRequest,
} from "@amadya/api-client";
import { Badge, Button, Checkbox, Dialog, DialogContent, DialogTitle, Input, NativeSelect, Switch, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@amadya/ui";
import { PlusIcon } from "lucide-react";
import { useState } from "react";
import { useStore } from "zustand";
import { ActiveBadge, emptyText, Field, LocalizedInputs, PageHeader } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";
import { session } from "@/lib/session";

function useIsAdmin() {
  return useStore(session, (s) => s.claims?.roles.includes("ADMIN") ?? false);
}

export function VatPage() {
  const t = useT();
  const { locale } = useLocale();
  const admin = useIsAdmin();
  const [editing, setEditing] = useState<VatRate | "new" | null>(null);
  const { data = [] } = useApi(["vat"], () => listVatRates());
  return (
    <>
      <PageHeader
        title={t("settings.vatTitle")}
        actions={
          admin && (
            <Button onClick={() => setEditing("new")}>
              <PlusIcon /> {t("settings.newVat")}
            </Button>
          )
        }
      />
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("common.code")}</TableHead>
            <TableHead>{t("common.name")}</TableHead>
            <TableHead className="text-right">{t("settings.percent")}</TableHead>
            <TableHead>{t("settings.fiscalGroup")}</TableHead>
            <TableHead>{t("common.status")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.map((v) => (
            <TableRow key={v.id} className={admin ? "cursor-pointer" : ""} onClick={() => admin && setEditing(v)}>
              <TableCell className="font-mono">{v.code}</TableCell>
              <TableCell>{lt(v.name, locale)}</TableCell>
              <TableCell className="text-right tabular-nums">{v.percent}%</TableCell>
              <TableCell>{v.fiscalGroup}</TableCell>
              <TableCell>
                <ActiveBadge active={v.active} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {editing && <VatDialog rate={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function VatDialog({ rate, onClose }: { rate?: VatRate; onClose: () => void }) {
  const t = useT();
  const [form, setForm] = useState<VatRateRequest>({ code: rate?.code ?? "", name: rate?.name ?? emptyText(), percent: rate?.percent ?? "", fiscalGroup: rate?.fiscalGroup ?? "", active: rate?.active ?? true });
  const save = useApiMutation((req: VatRateRequest) => (rate ? updateVatRate({ path: { id: rate.id }, body: req }) : createVatRate({ body: req })), {
    invalidate: [["vat"]],
    success: t("common.saved"),
    onSuccess: onClose,
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{rate?.code ?? t("settings.newVat")}</DialogTitle>
        <div className="grid grid-cols-3 gap-3">
          <Field label={t("common.code")}>
            <Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} />
          </Field>
          <Field label={t("settings.percent")}>
            <Input inputMode="decimal" value={form.percent} onChange={(e) => setForm({ ...form, percent: e.target.value.replace(",", ".") })} />
          </Field>
          <Field label={t("settings.fiscalGroup")}>
            <Input maxLength={2} value={form.fiscalGroup} onChange={(e) => setForm({ ...form, fiscalGroup: e.target.value.toUpperCase() })} />
          </Field>
        </div>
        <LocalizedInputs value={form.name} onChange={(name) => setForm({ ...form, name })} labelRo={t("common.nameRo")} labelEn={t("common.nameEn")} />
        <label className="flex items-center gap-2 text-sm font-medium">
          <Switch checked={form.active} onCheckedChange={(active) => setForm({ ...form, active })} /> {t("common.active")}
        </label>
        <Button disabled={!form.code || !form.percent || !form.fiscalGroup || save.isPending} onClick={() => save.mutate(form)}>
          {t("common.save")}
        </Button>
      </DialogContent>
    </Dialog>
  );
}

export function StationsPage() {
  const t = useT();
  const { locale } = useLocale();
  const admin = useIsAdmin();
  const [editing, setEditing] = useState<Station | "new" | null>(null);
  const { data = [] } = useApi(["stations"], () => listStations());
  return (
    <>
      <PageHeader
        title={t("settings.stationsTitle")}
        actions={
          admin && (
            <Button onClick={() => setEditing("new")}>
              <PlusIcon /> {t("settings.newStation")}
            </Button>
          )
        }
      />
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("common.code")}</TableHead>
            <TableHead>{t("common.name")}</TableHead>
            <TableHead className="text-right">{t("settings.parallelSlots")}</TableHead>
            <TableHead>{t("common.status")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.map((s) => (
            <TableRow key={s.id} className={admin ? "cursor-pointer" : ""} onClick={() => admin && setEditing(s)}>
              <TableCell className="font-mono">{s.code}</TableCell>
              <TableCell>{lt(s.name, locale)}</TableCell>
              <TableCell className="text-right tabular-nums">{s.parallelSlots}</TableCell>
              <TableCell>
                <ActiveBadge active={s.active} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {editing && <StationDialog station={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function StationDialog({ station, onClose }: { station?: Station; onClose: () => void }) {
  const t = useT();
  const [form, setForm] = useState<StationRequest>({ code: station?.code ?? "", name: station?.name ?? emptyText(), parallelSlots: station?.parallelSlots ?? 2, active: station?.active ?? true });
  const save = useApiMutation((req: StationRequest) => (station ? updateStation({ path: { id: station.id }, body: req }) : createStation({ body: req })), {
    invalidate: [["stations"]],
    success: t("common.saved"),
    onSuccess: onClose,
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{station?.code ?? t("settings.newStation")}</DialogTitle>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t("common.code")}>
            <Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, "") })} />
          </Field>
          <Field label={t("settings.parallelSlots")}>
            <Input type="number" min={1} value={form.parallelSlots} onChange={(e) => setForm({ ...form, parallelSlots: Number(e.target.value) })} />
          </Field>
        </div>
        <LocalizedInputs value={form.name} onChange={(name) => setForm({ ...form, name })} labelRo={t("common.nameRo")} labelEn={t("common.nameEn")} />
        <label className="flex items-center gap-2 text-sm font-medium">
          <Switch checked={form.active} onCheckedChange={(active) => setForm({ ...form, active })} /> {t("common.active")}
        </label>
        <Button disabled={!form.code || !form.name.ro || save.isPending} onClick={() => save.mutate(form)}>
          {t("common.save")}
        </Button>
      </DialogContent>
    </Dialog>
  );
}

const roles: Role[] = ["ADMIN", "MANAGER", "WAITER", "KITCHEN", "CASHIER"];

export function UsersPage() {
  const t = useT();
  const [editing, setEditing] = useState<User | "new" | null>(null);
  const { data = [] } = useApi(["users"], () => listUsers());
  return (
    <>
      <PageHeader
        title={t("users.title")}
        actions={
          <Button onClick={() => setEditing("new")}>
            <PlusIcon /> {t("users.new")}
          </Button>
        }
      />
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("common.name")}</TableHead>
            <TableHead>{t("procurement.email")}</TableHead>
            <TableHead>{t("users.roles")}</TableHead>
            <TableHead>{t("common.status")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.map((u) => (
            <TableRow key={u.id} className="cursor-pointer" onClick={() => setEditing(u)}>
              <TableCell className="font-medium">{u.name}</TableCell>
              <TableCell>{u.email}</TableCell>
              <TableCell className="space-x-1">
                {u.roles.map((r) => (
                  <Badge key={r} variant="outline">
                    {t(`users.roleNames.${r}`)}
                  </Badge>
                ))}
              </TableCell>
              <TableCell>
                <ActiveBadge active={u.active} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {editing && <UserDialog user={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function UserDialog({ user, onClose }: { user?: User; onClose: () => void }) {
  const t = useT();
  const [email, setEmail] = useState(user?.email ?? "");
  const [name, setName] = useState(user?.name ?? "");
  const [phone, setPhone] = useState(user?.phone ?? "");
  const [password, setPassword] = useState("");
  const [selected, setSelected] = useState<Role[]>(user?.roles ?? ["WAITER"]);
  const [locale, setLocale] = useState<Locale>(user?.locale ?? "ro");
  const [active, setActive] = useState(user?.active ?? true);
  const save = useApiMutation(
    () =>
      user
        ? updateUser({ path: { id: user.id }, body: { name, phone: phone || undefined, roles: selected, locale, active, password: password || undefined } })
        : createUser({ body: { email, name, phone: phone || undefined, password, roles: selected, locale } }),
    { invalidate: [["users"]], success: t("common.saved"), onSuccess: onClose },
  );
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{user ? t("users.edit") : t("users.new")}</DialogTitle>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("procurement.email")}>
            <Input type="email" value={email} disabled={!!user} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Field label={t("common.name")}>
            <Input value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label={t("procurement.phone")}>
            <Input value={phone} onChange={(e) => setPhone(e.target.value)} />
          </Field>
          <Field label={t("users.locale")}>
            <NativeSelect value={locale} onChange={(e) => setLocale(e.target.value as Locale)}>
              <option value="ro">Română</option>
              <option value="en">English</option>
            </NativeSelect>
          </Field>
        </div>
        <Field label={t("users.password")} hint={t("users.passwordHint")}>
          <Input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field label={t("users.roles")}>
          <div className="grid grid-cols-2 gap-2">
            {roles.map((r) => (
              <label key={r} className="flex items-center gap-2 text-sm">
                <Checkbox checked={selected.includes(r)} onCheckedChange={(c) => setSelected(c ? [...selected, r] : selected.filter((x) => x !== r))} />
                {t(`users.roleNames.${r}`)}
              </label>
            ))}
          </div>
        </Field>
        {user && (
          <label className="flex items-center gap-2 text-sm font-medium">
            <Switch checked={active} onCheckedChange={setActive} /> {t("common.active")}
          </label>
        )}
        <Button
          disabled={save.isPending || !name || selected.length === 0 || (!user && (!email || password.length < 10)) || (!!password && password.length < 10)}
          onClick={() => save.mutate(undefined)}
        >
          {t("common.save")}
        </Button>
      </DialogContent>
    </Dialog>
  );
}
