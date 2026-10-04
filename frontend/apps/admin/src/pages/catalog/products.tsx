import {
  createProduct,
  deleteProduct,
  getProductRecipe,
  listCategories,
  listModifierGroups,
  listProducts,
  listStations,
  listVatRates,
  replaceProductRecipe,
  updateProduct,
  type Product,
  type ProductKind,
  type ProductRequest,
} from "@amadya/api-client";
import {
  Badge,
  Button,
  Checkbox,
  Input,
  NativeSelect,
  Sheet,
  SheetContent,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@amadya/ui";
import { PlusIcon } from "lucide-react";
import { useState } from "react";
import { emptyText, Field, ImageUpload, LocalizedInputs, PageHeader, splitList } from "@/components/kit";
import { RecipeEditor } from "@/components/recipe-editor";
import { useApi, useApiMutation } from "@/lib/data";
import { formatMoney, lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

export function ProductsPage() {
  const t = useT();
  const { locale } = useLocale();
  const [categoryId, setCategoryId] = useState("");
  const [editing, setEditing] = useState<Product | "new" | null>(null);
  const { data: categories = [] } = useApi(["categories"], () => listCategories());
  const { data: products = [] } = useApi(["products", categoryId], () => listProducts({ query: { categoryId: categoryId || undefined } }));

  return (
    <>
      <PageHeader
        title={t("products.title")}
        actions={
          <>
            <NativeSelect value={categoryId} onChange={(e) => setCategoryId(e.target.value)} className="w-52">
              <option value="">{t("common.all")}</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {lt(c.name, locale)}
                </option>
              ))}
            </NativeSelect>
            <Button onClick={() => setEditing("new")}>
              <PlusIcon /> {t("products.new")}
            </Button>
          </>
        }
      />
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-14" />
            <TableHead>{t("common.name")}</TableHead>
            <TableHead>{t("products.category")}</TableHead>
            <TableHead>{t("products.kind")}</TableHead>
            <TableHead className="text-right">{t("common.price")}</TableHead>
            <TableHead>{t("common.status")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {products.map((p) => (
            <TableRow key={p.id} className="cursor-pointer" onClick={() => setEditing(p)}>
              <TableCell>
                {p.imageUrl ? <img src={p.imageUrl} alt="" className="size-10 rounded-lg object-cover" /> : <div className="size-10 rounded-lg bg-muted" />}
              </TableCell>
              <TableCell className="font-medium">{lt(p.name, locale)}</TableCell>
              <TableCell>{lt(categories.find((c) => c.id === p.categoryId)?.name, locale)}</TableCell>
              <TableCell>{t(`products.kinds.${p.kind}`)}</TableCell>
              <TableCell className="text-right tabular-nums">{formatMoney(p.price, locale)}</TableCell>
              <TableCell className="space-x-1">
                {p.archived ? <Badge variant="muted">{t("products.archived")}</Badge> : <Badge variant={p.available ? "success" : "outline"}>{p.available ? t("products.available") : "—"}</Badge>}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {editing && <ProductSheet product={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function toRequest(p?: Product): ProductRequest {
  return {
    categoryId: p?.categoryId ?? "",
    name: p?.name ?? emptyText(),
    description: p?.description ?? emptyText(),
    imageUrl: p?.imageUrl,
    price: p?.price.amount ?? "",
    vatRateId: p?.vatRateId ?? "",
    stationId: p?.stationId,
    kind: p?.kind ?? "RECIPE",
    prepTimeSec: p?.prepTimeSec ?? 300,
    available: p?.available ?? true,
    sortOrder: p?.sortOrder ?? 0,
    allergens: p?.allergens ?? [],
    modifierGroupIds: p?.modifierGroupIds ?? [],
  };
}

function ProductSheet({ product, onClose }: { product?: Product; onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const [form, setForm] = useState<ProductRequest>(() => toRequest(product));
  const set = (patch: Partial<ProductRequest>) => setForm((f) => ({ ...f, ...patch }));
  const { data: categories = [] } = useApi(["categories"], () => listCategories());
  const { data: vats = [] } = useApi(["vat"], () => listVatRates());
  const { data: stations = [] } = useApi(["stations"], () => listStations());
  const { data: groups = [] } = useApi(["modifier-groups"], () => listModifierGroups());
  const { data: recipe } = useApi(["recipe", product?.id], () => getProductRecipe({ path: { productId: product!.id } }), { enabled: !!product });

  const save = useApiMutation(
    (req: ProductRequest) => (product ? updateProduct({ path: { id: product.id }, body: req }) : createProduct({ body: req })),
    { invalidate: [["products"]], success: t("common.saved"), onSuccess: onClose },
  );
  const archive = useApiMutation(() => deleteProduct({ path: { id: product!.id } }), { invalidate: [["products"]], onSuccess: onClose });
  const saveRecipe = useApiMutation((req: Parameters<typeof replaceProductRecipe>[0]["body"]) => replaceProductRecipe({ path: { productId: product!.id }, body: req }), {
    invalidate: [["recipe", product?.id]],
    success: t("common.saved"),
  });
  const margin = recipe?.estimatedCost && Number(recipe.estimatedCost.amount) > 0 && Number(form.price) > 0 ? Math.round((1 - Number(recipe.estimatedCost.amount) / Number(form.price)) * 100) : undefined;

  return (
    <Sheet open onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="sm:max-w-2xl">
        <SheetHeader>
          <SheetTitle>{product ? lt(product.name, locale) : t("products.new")}</SheetTitle>
        </SheetHeader>
        <Tabs defaultValue="general" className="flex-1 overflow-y-auto px-5">
          <TabsList>
            <TabsTrigger value="general">{t("products.general")}</TabsTrigger>
            <TabsTrigger value="recipe" disabled={!product}>
              {t("products.recipe")}
            </TabsTrigger>
          </TabsList>
          <TabsContent value="general" className="space-y-4 pb-6">
            <LocalizedInputs value={form.name} onChange={(name) => set({ name })} labelRo={t("common.nameRo")} labelEn={t("common.nameEn")} />
            <LocalizedInputs value={form.description ?? emptyText()} onChange={(description) => set({ description })} labelRo={t("products.descriptionRo")} labelEn={t("products.descriptionEn")} multiline />
            <Field label={t("products.image")}>
              <ImageUpload value={form.imageUrl} onChange={(imageUrl) => set({ imageUrl })} />
            </Field>
            <div className="grid gap-3 sm:grid-cols-3">
              <Field label={t("products.category")}>
                <NativeSelect value={form.categoryId} onChange={(e) => set({ categoryId: e.target.value })} required>
                  <option value="">{t("common.choose")}</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {lt(c.name, locale)}
                    </option>
                  ))}
                </NativeSelect>
              </Field>
              <Field label={t("products.price")}>
                <Input inputMode="decimal" value={form.price} onChange={(e) => set({ price: e.target.value.replace(",", ".") })} />
              </Field>
              <Field label={t("products.vat")}>
                <NativeSelect value={form.vatRateId} onChange={(e) => set({ vatRateId: e.target.value })}>
                  <option value="">{t("common.choose")}</option>
                  {vats.map((v) => (
                    <option key={v.id} value={v.id}>
                      {lt(v.name, locale)}
                    </option>
                  ))}
                </NativeSelect>
              </Field>
              <Field label={t("products.kind")}>
                <NativeSelect value={form.kind} onChange={(e) => set({ kind: e.target.value as ProductKind })}>
                  {(["RECIPE", "RESALE", "SERVICE"] as const).map((k) => (
                    <option key={k} value={k}>
                      {t(`products.kinds.${k}`)}
                    </option>
                  ))}
                </NativeSelect>
              </Field>
              <Field label={t("products.station")}>
                <NativeSelect value={form.stationId ?? ""} onChange={(e) => set({ stationId: e.target.value || undefined })}>
                  <option value="">{t("products.noStation")}</option>
                  {stations.map((s) => (
                    <option key={s.id} value={s.id}>
                      {lt(s.name, locale)}
                    </option>
                  ))}
                </NativeSelect>
              </Field>
              <Field label={t("products.prepTime")}>
                <Input type="number" min={0} value={Math.round((form.prepTimeSec ?? 0) / 60)} onChange={(e) => set({ prepTimeSec: Number(e.target.value) * 60 })} />
              </Field>
            </div>
            <Field label={t("products.allergens")} hint={t("products.allergensHint")}>
              <Input value={(form.allergens ?? []).join(", ")} onChange={(e) => set({ allergens: splitList(e.target.value) })} />
            </Field>
            <Field label={t("products.modifierGroups")}>
              <div className="grid gap-2 sm:grid-cols-2">
                {groups.map((g) => {
                  const checked = form.modifierGroupIds?.includes(g.id) ?? false;
                  return (
                    <label key={g.id} className="flex items-center gap-2 rounded-lg border p-2 text-sm">
                      <Checkbox
                        checked={checked}
                        onCheckedChange={(c) => set({ modifierGroupIds: c ? [...(form.modifierGroupIds ?? []), g.id] : form.modifierGroupIds?.filter((id) => id !== g.id) })}
                      />
                      {lt(g.name, locale)}
                    </label>
                  );
                })}
              </div>
            </Field>
            <div className="flex items-center gap-6">
              <label className="flex items-center gap-2 text-sm font-medium">
                <Switch checked={form.available} onCheckedChange={(available) => set({ available })} /> {t("products.available")}
              </label>
              <Field label={t("products.sortOrder")} className="w-24">
                <Input type="number" value={form.sortOrder ?? 0} onChange={(e) => set({ sortOrder: Number(e.target.value) })} />
              </Field>
            </div>
          </TabsContent>
          <TabsContent value="recipe" className="pb-6">
            {margin !== undefined && <p className="mb-3 text-sm font-semibold">{t("products.margin", { value: margin })}</p>}
            <RecipeEditor recipe={recipe} onSave={(req) => saveRecipe.mutate(req)} saving={saveRecipe.isPending} />
          </TabsContent>
        </Tabs>
        <SheetFooter className="flex-row">
          {product && !product.archived && (
            <Button variant="outline" onClick={() => confirm(t("common.confirmDelete")) && archive.mutate(undefined)}>
              {t("common.delete")}
            </Button>
          )}
          <Button className="flex-1" disabled={save.isPending || !form.name.ro || !form.categoryId || !form.vatRateId || !(Number(form.price) >= 0)} onClick={() => save.mutate(form)}>
            {t("common.save")}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
