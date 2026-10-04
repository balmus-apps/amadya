import {
  createModifierGroup,
  deleteModifierGroup,
  getOptionRecipe,
  listModifierGroups,
  replaceOptionRecipe,
  updateModifierGroup,
  type AdminModifierGroup,
  type ModifierGroupRequest,
  type RecipeRequest,
} from "@amadya/api-client";
import { Badge, Button, Card, CardContent, Dialog, DialogContent, DialogTitle, Input, Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle, Switch } from "@amadya/ui";
import { PlusIcon, Trash2Icon, UtensilsIcon } from "lucide-react";
import { useState } from "react";
import { emptyText, Field, LocalizedInputs, PageHeader } from "@/components/kit";
import { RecipeEditor } from "@/components/recipe-editor";
import { useApi, useApiMutation } from "@/lib/data";
import { formatMoney, lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

export function ModifiersPage() {
  const t = useT();
  const { locale } = useLocale();
  const [editing, setEditing] = useState<AdminModifierGroup | "new" | null>(null);
  const { data = [] } = useApi(["modifier-groups"], () => listModifierGroups());
  return (
    <>
      <PageHeader
        title={t("modifiers.title")}
        actions={
          <Button onClick={() => setEditing("new")}>
            <PlusIcon /> {t("modifiers.new")}
          </Button>
        }
      />
      <div className="grid gap-4 md:grid-cols-2">
        {data.map((g) => (
          <Card key={g.id} className="cursor-pointer transition hover:shadow-md" onClick={() => setEditing(g)}>
            <CardContent className="space-y-3 p-5">
              <div className="flex items-center justify-between">
                <h3 className="text-lg font-bold">{lt(g.name, locale)}</h3>
                <Badge variant={g.minSelect > 0 ? "accent" : "muted"}>{t("modifiers.rule", { min: g.minSelect, max: g.maxSelect })}</Badge>
              </div>
              <ul className="flex flex-wrap gap-2">
                {g.options.map((o) => (
                  <li key={o.id}>
                    <Badge variant="outline">
                      {lt(o.name, locale)}
                      {Number(o.priceDelta.amount) !== 0 && ` +${formatMoney(o.priceDelta, locale)}`}
                    </Badge>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        ))}
      </div>
      {editing && <GroupSheet group={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function GroupSheet({ group, onClose }: { group?: AdminModifierGroup; onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const [recipeFor, setRecipeFor] = useState<{ id: string; name: string } | null>(null);
  const [form, setForm] = useState<ModifierGroupRequest>({
    name: group?.name ?? emptyText(),
    minSelect: group?.minSelect ?? 0,
    maxSelect: group?.maxSelect ?? 1,
    options: group?.options.map((o) => ({ id: o.id, name: o.name, priceDelta: o.priceDelta.amount, available: o.available, sortOrder: o.sortOrder })) ?? [{ name: emptyText(), priceDelta: "0" }],
  });
  const save = useApiMutation((req: ModifierGroupRequest) => (group ? updateModifierGroup({ path: { id: group.id }, body: req }) : createModifierGroup({ body: req })), {
    invalidate: [["modifier-groups"]],
    success: t("common.saved"),
    onSuccess: onClose,
  });
  const remove = useApiMutation(() => deleteModifierGroup({ path: { id: group!.id } }), { invalidate: [["modifier-groups"]], onSuccess: onClose });
  const setOption = (i: number, patch: Partial<ModifierGroupRequest["options"][number]>) =>
    setForm((f) => ({ ...f, options: f.options.map((o, j) => (j === i ? { ...o, ...patch } : o)) }));

  return (
    <Sheet open onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="sm:max-w-xl">
        <SheetHeader>
          <SheetTitle>{group ? lt(group.name, locale) : t("modifiers.new")}</SheetTitle>
        </SheetHeader>
        <div className="flex-1 space-y-4 overflow-y-auto px-5 pb-6">
          <LocalizedInputs value={form.name} onChange={(name) => setForm({ ...form, name })} labelRo={t("common.nameRo")} labelEn={t("common.nameEn")} />
          <div className="grid grid-cols-2 gap-3">
            <Field label={t("modifiers.minSelect")}>
              <Input type="number" min={0} value={form.minSelect} onChange={(e) => setForm({ ...form, minSelect: Number(e.target.value) })} />
            </Field>
            <Field label={t("modifiers.maxSelect")}>
              <Input type="number" min={1} value={form.maxSelect} onChange={(e) => setForm({ ...form, maxSelect: Number(e.target.value) })} />
            </Field>
          </div>
          <h3 className="font-bold">{t("modifiers.options")}</h3>
          {form.options.map((o, i) => (
            <div key={o.id ?? i} className="space-y-2 rounded-xl border p-3">
              <div className="grid grid-cols-2 gap-2">
                <Input placeholder={t("common.nameRo")} value={o.name.ro} onChange={(e) => setOption(i, { name: { ...o.name, ro: e.target.value } })} />
                <Input placeholder={t("common.nameEn")} value={o.name.en ?? ""} onChange={(e) => setOption(i, { name: { ...o.name, en: e.target.value } })} />
              </div>
              <div className="flex items-center gap-2">
                <Input className="w-32" inputMode="decimal" placeholder={t("modifiers.priceDelta")} value={o.priceDelta ?? "0"} onChange={(e) => setOption(i, { priceDelta: e.target.value.replace(",", ".") })} />
                <label className="flex items-center gap-2 text-sm">
                  <Switch checked={o.available ?? true} onCheckedChange={(available) => setOption(i, { available })} />
                </label>
                {o.id && (
                  <Button variant="ghost" size="sm" onClick={() => setRecipeFor({ id: o.id!, name: lt(o.name, locale) })}>
                    <UtensilsIcon /> {t("modifiers.recipe")}
                  </Button>
                )}
                <Button variant="ghost" size="icon-sm" className="ml-auto" onClick={() => setForm({ ...form, options: form.options.filter((_, j) => j !== i) })} aria-label={t("common.remove")}>
                  <Trash2Icon />
                </Button>
              </div>
            </div>
          ))}
          <Button variant="outline" size="sm" onClick={() => setForm({ ...form, options: [...form.options, { name: emptyText(), priceDelta: "0" }] })}>
            <PlusIcon /> {t("modifiers.addOption")}
          </Button>
        </div>
        <SheetFooter className="flex-row">
          {group && (
            <Button variant="outline" onClick={() => confirm(t("common.confirmDelete")) && remove.mutate(undefined)}>
              {t("common.delete")}
            </Button>
          )}
          <Button className="flex-1" disabled={save.isPending || !form.name.ro || form.options.some((o) => !o.name.ro)} onClick={() => save.mutate(form)}>
            {t("common.save")}
          </Button>
        </SheetFooter>
        {recipeFor && <OptionRecipeDialog option={recipeFor} onClose={() => setRecipeFor(null)} />}
      </SheetContent>
    </Sheet>
  );
}

function OptionRecipeDialog({ option, onClose }: { option: { id: string; name: string }; onClose: () => void }) {
  const t = useT();
  const { data: recipe } = useApi(["option-recipe", option.id], () => getOptionRecipe({ path: { optionId: option.id } }));
  const save = useApiMutation((req: RecipeRequest) => replaceOptionRecipe({ path: { optionId: option.id }, body: req }), {
    invalidate: [["option-recipe", option.id]],
    success: t("common.saved"),
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-xl">
        <DialogTitle>
          {t("modifiers.recipe")}: {option.name}
        </DialogTitle>
        <RecipeEditor recipe={recipe} onSave={(req) => save.mutate(req)} saving={save.isPending} />
      </DialogContent>
    </Dialog>
  );
}
