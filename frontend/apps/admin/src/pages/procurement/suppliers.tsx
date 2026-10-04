import { createSupplier, listSuppliers, updateSupplier, type Supplier, type SupplierRequest } from "@amadya/api-client";
import { Button, Dialog, DialogContent, DialogTitle, Input, Switch, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@amadya/ui";
import { PlusIcon } from "lucide-react";
import { useState } from "react";
import { ActiveBadge, Field, PageHeader } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { useT } from "@/lib/i18n";

export function SuppliersPage() {
  const t = useT();
  const [editing, setEditing] = useState<Supplier | "new" | null>(null);
  const { data = [] } = useApi(["suppliers"], () => listSuppliers());
  return (
    <>
      <PageHeader
        title={t("procurement.suppliersTitle")}
        actions={
          <Button onClick={() => setEditing("new")}>
            <PlusIcon /> {t("procurement.newSupplier")}
          </Button>
        }
      />
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("common.name")}</TableHead>
            <TableHead>{t("procurement.cui")}</TableHead>
            <TableHead>{t("procurement.phone")}</TableHead>
            <TableHead>{t("procurement.email")}</TableHead>
            <TableHead>{t("common.status")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.map((s) => (
            <TableRow key={s.id} className="cursor-pointer" onClick={() => setEditing(s)}>
              <TableCell className="font-medium">{s.name}</TableCell>
              <TableCell className="font-mono text-xs">{s.cui}</TableCell>
              <TableCell>{s.phone}</TableCell>
              <TableCell>{s.email}</TableCell>
              <TableCell>
                <ActiveBadge active={s.active} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {editing && <SupplierDialog supplier={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function SupplierDialog({ supplier, onClose }: { supplier?: Supplier; onClose: () => void }) {
  const t = useT();
  const [form, setForm] = useState<SupplierRequest>({ ...supplier, name: supplier?.name ?? "", active: supplier?.active ?? true });
  const save = useApiMutation((req: SupplierRequest) => (supplier ? updateSupplier({ path: { id: supplier.id }, body: req }) : createSupplier({ body: req })), {
    invalidate: [["suppliers"]],
    success: t("common.saved"),
    onSuccess: onClose,
  });
  const text = (key: keyof SupplierRequest, label: string) => (
    <Field label={label}>
      <Input value={(form[key] as string | undefined) ?? ""} onChange={(e) => setForm({ ...form, [key]: e.target.value })} />
    </Field>
  );
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-xl">
        <DialogTitle>{supplier?.name ?? t("procurement.newSupplier")}</DialogTitle>
        {text("name", t("common.name"))}
        <div className="grid gap-3 sm:grid-cols-2">
          {text("cui", t("procurement.cui"))}
          {text("regCom", t("procurement.regCom"))}
          {text("phone", t("procurement.phone"))}
          {text("email", t("procurement.email"))}
        </div>
        {text("address", t("procurement.address"))}
        {text("iban", t("procurement.iban"))}
        <label className="flex items-center gap-2 text-sm font-medium">
          <Switch checked={form.active} onCheckedChange={(active) => setForm({ ...form, active })} /> {t("common.active")}
        </label>
        <Button disabled={!form.name || save.isPending} onClick={() => save.mutate(form)}>
          {t("common.save")}
        </Button>
      </DialogContent>
    </Dialog>
  );
}
