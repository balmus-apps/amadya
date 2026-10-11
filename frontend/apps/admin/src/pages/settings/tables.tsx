import {
  createDiningTable,
  createFloorArea,
  listDiningTables,
  listFloorAreas,
  updateDiningTable,
  updateFloorArea,
  type DiningTableAdmin,
  type DiningTableRequest,
  type FloorAreaAdmin,
  type FloorAreaRequest,
} from "@amadya/api-client";
import { Button, Dialog, DialogContent, DialogTitle, Input, NativeSelect, Switch } from "@amadya/ui";
import { PencilIcon, PlusIcon, UsersIcon } from "lucide-react";
import { useState } from "react";
import { ActiveBadge, Empty, emptyText, Field, LocalizedInputs, PageHeader } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

/** Floor areas (salon, terrace) and their tables, as the waiter app shows them. */
export function TablesPage() {
  const t = useT();
  const { locale } = useLocale();
  const areas = useApi(["floor-areas"], () => listFloorAreas());
  const tables = useApi(["dining-tables"], () => listDiningTables());
  const [area, setArea] = useState<FloorAreaAdmin | "new" | null>(null);
  const [table, setTable] = useState<{ table?: DiningTableAdmin; areaId: string } | null>(null);

  return (
    <>
      <PageHeader
        title={t("tables.title")}
        description={t("tables.description")}
        actions={
          <Button onClick={() => setArea("new")}>
            <PlusIcon /> {t("tables.newArea")}
          </Button>
        }
      />
      {(areas.data ?? []).length === 0 ? (
        <Empty loading={areas.isPending}>{t("tables.noAreas")}</Empty>
      ) : (
        <div className="space-y-6">
          {areas.data!.map((a) => {
            const inArea = (tables.data ?? []).filter((tb) => tb.areaId === a.id);
            return (
              <section key={a.id} className="rounded-2xl border bg-card p-4">
                <div className="mb-3 flex flex-wrap items-center gap-3">
                  <h2 className="text-lg font-bold">{lt(a.name, locale)}</h2>
                  <ActiveBadge active={a.active} />
                  <span className="text-sm text-muted-foreground">{t("tables.count", { count: inArea.length })}</span>
                  <div className="ml-auto flex gap-2">
                    <Button variant="ghost" size="sm" onClick={() => setArea(a)}>
                      <PencilIcon /> {t("common.edit")}
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => setTable({ areaId: a.id })}>
                      <PlusIcon /> {t("tables.newTable")}
                    </Button>
                  </div>
                </div>
                {inArea.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{t("tables.noTables")}</p>
                ) : (
                  <ul className="grid grid-cols-[repeat(auto-fill,minmax(7rem,1fr))] gap-2">
                    {inArea.map((tb) => (
                      <li key={tb.id}>
                        <button
                          onClick={() => setTable({ table: tb, areaId: a.id })}
                          className="flex h-20 w-full flex-col items-center justify-center rounded-xl border-2 border-dashed bg-background transition hover:border-primary data-[inactive=true]:opacity-50"
                          data-inactive={!tb.active}
                          aria-label={t("tables.edit", { label: tb.label })}
                        >
                          <span className="text-2xl font-black">{tb.label}</span>
                          <span className="flex items-center gap-1 text-xs text-muted-foreground">
                            <UsersIcon className="size-3" /> {tb.seats}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            );
          })}
        </div>
      )}
      {area && <AreaDialog area={area === "new" ? undefined : area} nextOrder={(areas.data?.length ?? 0) + 1} onClose={() => setArea(null)} />}
      {table && <TableDialog table={table.table} areaId={table.areaId} areas={areas.data ?? []} nextOrder={(tables.data ?? []).filter((tb) => tb.areaId === table.areaId).length + 1} onClose={() => setTable(null)} />}
    </>
  );
}

function AreaDialog({ area, nextOrder, onClose }: { area?: FloorAreaAdmin; nextOrder: number; onClose: () => void }) {
  const t = useT();
  const [form, setForm] = useState<FloorAreaRequest>({ name: area?.name ?? emptyText(), sortOrder: area?.sortOrder ?? nextOrder, active: area?.active ?? true });
  const save = useApiMutation((req: FloorAreaRequest) => (area ? updateFloorArea({ path: { id: area.id }, body: req }) : createFloorArea({ body: req })), {
    invalidate: [["floor-areas"]],
    success: t("common.saved"),
    onSuccess: onClose,
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{area ? t("tables.editArea") : t("tables.newArea")}</DialogTitle>
        <LocalizedInputs value={form.name} onChange={(name) => setForm({ ...form, name })} labelRo={t("common.nameRo")} labelEn={t("common.nameEn")} />
        <Field label={t("tables.sortOrder")}>
          <Input type="number" value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })} />
        </Field>
        <label className="flex items-center gap-2 text-sm font-medium">
          <Switch checked={form.active} onCheckedChange={(active) => setForm({ ...form, active })} /> {t("common.active")}
        </label>
        <Button disabled={!form.name.ro.trim() || save.isPending} onClick={() => save.mutate(form)}>
          {t("common.save")}
        </Button>
      </DialogContent>
    </Dialog>
  );
}

function TableDialog({ table, areaId, areas, nextOrder, onClose }: { table?: DiningTableAdmin; areaId: string; areas: FloorAreaAdmin[]; nextOrder: number; onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const [form, setForm] = useState<DiningTableRequest>({ areaId: table?.areaId ?? areaId, label: table?.label ?? "", seats: table?.seats ?? 4, sortOrder: table?.sortOrder ?? nextOrder, active: table?.active ?? true });
  const save = useApiMutation((req: DiningTableRequest) => (table ? updateDiningTable({ path: { id: table.id }, body: req }) : createDiningTable({ body: req })), {
    invalidate: [["dining-tables"]],
    success: t("common.saved"),
    onSuccess: onClose,
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{table ? t("tables.edit", { label: table.label }) : t("tables.newTable")}</DialogTitle>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t("tables.label")} hint={t("tables.labelHint")}>
            <Input value={form.label} maxLength={12} onChange={(e) => setForm({ ...form, label: e.target.value })} />
          </Field>
          <Field label={t("tables.seats")}>
            <Input type="number" min={1} max={50} value={form.seats} onChange={(e) => setForm({ ...form, seats: Number(e.target.value) })} />
          </Field>
          <Field label={t("tables.area")}>
            <NativeSelect value={form.areaId} onChange={(e) => setForm({ ...form, areaId: e.target.value })}>
              {areas.map((a) => (
                <option key={a.id} value={a.id}>
                  {lt(a.name, locale)}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label={t("tables.sortOrder")}>
            <Input type="number" value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })} />
          </Field>
        </div>
        <label className="flex items-center gap-2 text-sm font-medium">
          <Switch checked={form.active} onCheckedChange={(active) => setForm({ ...form, active })} /> {t("common.active")}
        </label>
        <Button disabled={!form.label.trim() || form.seats < 1 || save.isPending} onClick={() => save.mutate(form)}>
          {t("common.save")}
        </Button>
      </DialogContent>
    </Dialog>
  );
}
