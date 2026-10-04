import { createCategory, deleteCategory, listCategories, updateCategory, type Category, type CategoryRequest } from "@amadya/api-client";
import { Button, Dialog, DialogContent, DialogTitle, Input, Switch, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@amadya/ui";
import { PlusIcon } from "lucide-react";
import { useState } from "react";
import { ActiveBadge, emptyText, Field, LocalizedInputs, PageHeader } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

export function CategoriesPage() {
  const t = useT();
  const { locale } = useLocale();
  const [editing, setEditing] = useState<Category | "new" | null>(null);
  const { data = [] } = useApi(["categories"], () => listCategories());
  return (
    <>
      <PageHeader
        title={t("categories.title")}
        actions={
          <Button onClick={() => setEditing("new")}>
            <PlusIcon /> {t("categories.new")}
          </Button>
        }
      />
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-20">{t("categories.sortOrder")}</TableHead>
            <TableHead>{t("common.nameRo")}</TableHead>
            <TableHead>{t("common.nameEn")}</TableHead>
            <TableHead>{t("common.status")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.map((c) => (
            <TableRow key={c.id} className="cursor-pointer" onClick={() => setEditing(c)}>
              <TableCell className="tabular-nums">{c.sortOrder}</TableCell>
              <TableCell className="font-medium">{c.name.ro}</TableCell>
              <TableCell>{c.name.en}</TableCell>
              <TableCell>
                <ActiveBadge active={c.active} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {editing && <CategoryDialog category={editing === "new" ? undefined : editing} title={editing === "new" ? t("categories.new") : lt(editing.name, locale)} onClose={() => setEditing(null)} />}
    </>
  );
}

function CategoryDialog({ category, title, onClose }: { category?: Category; title: string; onClose: () => void }) {
  const t = useT();
  const [form, setForm] = useState<CategoryRequest>({ name: category?.name ?? emptyText(), sortOrder: category?.sortOrder ?? 0, active: category?.active ?? true });
  const save = useApiMutation((req: CategoryRequest) => (category ? updateCategory({ path: { id: category.id }, body: req }) : createCategory({ body: req })), {
    invalidate: [["categories"]],
    success: t("common.saved"),
    onSuccess: onClose,
  });
  const remove = useApiMutation(() => deleteCategory({ path: { id: category!.id } }), { invalidate: [["categories"]], success: t("common.deleted"), onSuccess: onClose });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{title}</DialogTitle>
        <LocalizedInputs value={form.name} onChange={(name) => setForm({ ...form, name })} labelRo={t("common.nameRo")} labelEn={t("common.nameEn")} />
        <div className="flex items-end gap-6">
          <Field label={t("categories.sortOrder")} className="w-28">
            <Input type="number" value={form.sortOrder ?? 0} onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })} />
          </Field>
          <label className="flex items-center gap-2 pb-2 text-sm font-medium">
            <Switch checked={form.active} onCheckedChange={(active) => setForm({ ...form, active })} /> {t("common.active")}
          </label>
        </div>
        <div className="flex gap-2">
          {category && (
            <Button variant="outline" onClick={() => confirm(t("common.confirmDelete")) && remove.mutate(undefined)}>
              {t("common.delete")}
            </Button>
          )}
          <Button className="flex-1" disabled={!form.name.ro || save.isPending} onClick={() => save.mutate(form)}>
            {t("common.save")}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
