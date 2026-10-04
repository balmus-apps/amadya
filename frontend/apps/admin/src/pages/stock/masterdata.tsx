import {
  createMeasureUnit,
  createWarehouse,
  listMeasureUnits,
  listWarehouses,
  updateMeasureUnit,
  updateWarehouse,
  type Dimension,
  type MeasureUnit,
  type MeasureUnitRequest,
  type Warehouse,
  type WarehouseRequest,
  type WarehouseType,
} from "@amadya/api-client";
import { Badge, Button, Dialog, DialogContent, DialogTitle, Input, NativeSelect, Switch, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@amadya/ui";
import { PlusIcon } from "lucide-react";
import { useState } from "react";
import { ActiveBadge, emptyText, Field, LocalizedInputs, PageHeader, splitList } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

export function WarehousesPage() {
  const t = useT();
  const { locale } = useLocale();
  const [editing, setEditing] = useState<Warehouse | "new" | null>(null);
  const { data = [] } = useApi(["warehouses"], () => listWarehouses());
  return (
    <>
      <PageHeader
        title={t("stock.warehousesTitle")}
        actions={
          <Button onClick={() => setEditing("new")}>
            <PlusIcon /> {t("stock.newWarehouse")}
          </Button>
        }
      />
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("common.code")}</TableHead>
            <TableHead>{t("common.name")}</TableHead>
            <TableHead>{t("common.type")}</TableHead>
            <TableHead>{t("common.status")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.map((w) => (
            <TableRow key={w.id} className="cursor-pointer" onClick={() => setEditing(w)}>
              <TableCell className="font-mono text-xs">{w.code}</TableCell>
              <TableCell className="font-medium">{lt(w.name, locale)}</TableCell>
              <TableCell>{t(`stock.warehouseTypes.${w.type}`)}</TableCell>
              <TableCell>
                <ActiveBadge active={w.active} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {editing && <WarehouseDialog warehouse={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function WarehouseDialog({ warehouse, onClose }: { warehouse?: Warehouse; onClose: () => void }) {
  const t = useT();
  const [form, setForm] = useState<WarehouseRequest>({ code: warehouse?.code ?? "", name: warehouse?.name ?? emptyText(), type: warehouse?.type ?? "INGREDIENTS", active: warehouse?.active ?? true });
  const save = useApiMutation((req: WarehouseRequest) => (warehouse ? updateWarehouse({ path: { id: warehouse.id }, body: req }) : createWarehouse({ body: req })), {
    invalidate: [["warehouses"]],
    success: t("common.saved"),
    onSuccess: onClose,
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{warehouse ? warehouse.code : t("stock.newWarehouse")}</DialogTitle>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("common.code")}>
            <Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, "") })} />
          </Field>
          <Field label={t("common.type")}>
            <NativeSelect value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as WarehouseType })}>
              {(["INGREDIENTS", "FINISHED_GOODS", "FIXED_ASSETS", "CONSUMABLES"] as const).map((ty) => (
                <option key={ty} value={ty}>
                  {t(`stock.warehouseTypes.${ty}`)}
                </option>
              ))}
            </NativeSelect>
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

export function UnitsPage() {
  const t = useT();
  const { locale } = useLocale();
  const [editing, setEditing] = useState<MeasureUnit | "new" | null>(null);
  const { data = [] } = useApi(["units"], () => listMeasureUnits());
  return (
    <>
      <PageHeader
        title={t("stock.unitsTitle")}
        actions={
          <Button onClick={() => setEditing("new")}>
            <PlusIcon /> {t("stock.newUnit")}
          </Button>
        }
      />
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("common.code")}</TableHead>
            <TableHead>{t("common.name")}</TableHead>
            <TableHead>{t("stock.dimension")}</TableHead>
            <TableHead className="text-right">{t("stock.factor")}</TableHead>
            <TableHead>{t("stock.aliases")}</TableHead>
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.map((u) => (
            <TableRow key={u.id} className={u.status === "UNMAPPED" ? "bg-accent/10" : ""}>
              <TableCell className="font-mono font-semibold">{u.code}</TableCell>
              <TableCell>{lt(u.name, locale)}</TableCell>
              <TableCell>{u.dimension ? t(`stock.dimensions.${u.dimension}`) : <Badge variant="accent">{t("stock.unmapped")}</Badge>}</TableCell>
              <TableCell className="text-right tabular-nums">{u.factor}</TableCell>
              <TableCell className="text-xs text-muted-foreground">{u.aliases.join(", ")}</TableCell>
              <TableCell className="text-right">
                {u.isReference ? (
                  <Badge variant="secondary">{t("stock.reference")}</Badge>
                ) : (
                  <Button variant={u.status === "UNMAPPED" ? "default" : "ghost"} size="sm" onClick={() => setEditing(u)}>
                    {u.status === "UNMAPPED" ? t("stock.mapUnit") : t("common.edit")}
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {editing && <UnitDialog unit={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function UnitDialog({ unit, onClose }: { unit?: MeasureUnit; onClose: () => void }) {
  const t = useT();
  const [form, setForm] = useState<MeasureUnitRequest>({
    code: unit?.code ?? "",
    name: unit?.name ?? emptyText(),
    dimension: unit?.dimension ?? "MASS",
    factor: unit?.factor || "1",
    aliases: unit?.aliases ?? [],
  });
  const save = useApiMutation((req: MeasureUnitRequest) => (unit ? updateMeasureUnit({ path: { id: unit.id }, body: req }) : createMeasureUnit({ body: req })), {
    invalidate: [["units"]],
    success: t("common.saved"),
    onSuccess: onClose,
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{unit ? unit.code : t("stock.newUnit")}</DialogTitle>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label={t("common.code")}>
            <Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
          </Field>
          <Field label={t("stock.dimension")}>
            <NativeSelect value={form.dimension} onChange={(e) => setForm({ ...form, dimension: e.target.value as Dimension })}>
              {(["MASS", "VOLUME", "COUNT", "LENGTH"] as const).map((d) => (
                <option key={d} value={d}>
                  {t(`stock.dimensions.${d}`)}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label={t("stock.factor")}>
            <Input inputMode="decimal" value={form.factor} onChange={(e) => setForm({ ...form, factor: e.target.value.replace(",", ".") })} />
          </Field>
        </div>
        <p className="text-xs text-muted-foreground">{t("stock.factorHint")}</p>
        <LocalizedInputs value={form.name} onChange={(name) => setForm({ ...form, name })} labelRo={t("common.nameRo")} labelEn={t("common.nameEn")} />
        <Field label={t("stock.aliases")} hint={t("stock.aliasesHint")}>
          <Input value={(form.aliases ?? []).join(", ")} onChange={(e) => setForm({ ...form, aliases: splitList(e.target.value) })} />
        </Field>
        <Button disabled={!form.code || !form.name.ro || !(Number(form.factor) > 0) || save.isPending} onClick={() => save.mutate(form)}>
          {t("common.save")}
        </Button>
      </DialogContent>
    </Dialog>
  );
}
