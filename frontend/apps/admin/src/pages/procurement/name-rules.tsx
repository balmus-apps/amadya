import {
  createProductNameRule,
  deleteProductNameRule,
  listProductNameRules,
  listStockItems,
  testProductNameRule,
  updateProductNameRule,
  type ProductNameMatchType,
  type ProductNameRule,
  type ProductNameRuleRequest,
  type ProductNameTestResult,
} from "@amadya/api-client";
import { Badge, Button, Card, CardContent, Dialog, DialogContent, DialogTitle, Input, NativeSelect, Switch, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@amadya/ui";
import { ArrowDownIcon, ArrowUpIcon, PlusIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";
import { Empty, Field, PageHeader } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

const TYPES: ProductNameMatchType[] = ["CONTAINS", "STARTS_WITH", "REGEX"];

/** Keyword rules: "LAPTE|MILK" → "Lapte". Tried top to bottom; the first match names the product. */
export function NameRulesPage() {
  const t = useT();
  const { locale } = useLocale();
  const [editing, setEditing] = useState<ProductNameRule | "new" | null>(null);
  const [sample, setSample] = useState("");
  const [result, setResult] = useState<ProductNameTestResult>();
  const { data: rules = [], isLoading } = useApi(["name-rules"], () => listProductNameRules());
  const { data: items = [] } = useApi(["stock-items"], () => listStockItems());

  const toRequest = (r: ProductNameRule, patch: Partial<ProductNameRuleRequest> = {}): ProductNameRuleRequest => ({
    matchType: r.matchType,
    pattern: r.pattern,
    targetName: r.targetName,
    stockItemId: r.stockItemId,
    priority: r.priority,
    active: r.active,
    ...patch,
  });
  const update = useApiMutation((v: { rule: ProductNameRule; patch: Partial<ProductNameRuleRequest> }) => updateProductNameRule({ path: { id: v.rule.id }, body: toRequest(v.rule, v.patch) }), {
    invalidate: [["name-rules"]],
  });
  /** Renumbers 10, 20, 30… in the new order so moves stay simple. */
  const reorder = useApiMutation(
    async (ordered: ProductNameRule[]) => {
      let last: Awaited<ReturnType<typeof updateProductNameRule>> | undefined;
      for (const [i, r] of ordered.entries()) {
        const priority = (i + 1) * 10;
        if (r.priority !== priority) {
          last = await updateProductNameRule({ path: { id: r.id }, body: toRequest(r, { priority }) });
          if (last.error) return last;
        }
      }
      return last ?? { data: undefined };
    },
    { invalidate: [["name-rules"]] },
  );
  const remove = useApiMutation((id: string) => deleteProductNameRule({ path: { id } }), { invalidate: [["name-rules"]], success: t("common.deleted") });
  const test = useApiMutation(() => testProductNameRule({ body: { text: sample } }), { onSuccess: setResult });

  const move = (index: number, by: -1 | 1) => {
    const next = [...rules];
    const [rule] = next.splice(index, 1);
    if (!rule) return;
    next.splice(index + by, 0, rule);
    reorder.mutate(next);
  };

  return (
    <>
      <PageHeader
        title={t("nameRules.title")}
        description={t("nameRules.description")}
        actions={
          <Button onClick={() => setEditing("new")}>
            <PlusIcon /> {t("nameRules.new")}
          </Button>
        }
      />
      <Card className="mb-6">
        <CardContent className="flex flex-wrap items-end gap-3 py-4">
          <Field label={t("nameRules.testLabel")} className="min-w-72 flex-1">
            <Input value={sample} placeholder="LAPTE ZUZU 3,7% 1L" onChange={(e) => setSample(e.target.value)} onKeyDown={(e) => e.key === "Enter" && sample.trim() && test.mutate(undefined)} />
          </Field>
          <Button variant="outline" disabled={!sample.trim() || test.isPending} onClick={() => test.mutate(undefined)}>
            {t("nameRules.test")}
          </Button>
          {result && (
            <p className="text-sm">
              → <b>{result.name}</b> {result.ruleId ? <Badge variant="muted">{t("invoiceNew.byRule")}</Badge> : <Badge variant="outline">{t("nameRules.noRule")}</Badge>}
            </p>
          )}
        </CardContent>
      </Card>
      {rules.length === 0 ? (
        <Empty loading={isLoading} />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-20">{t("nameRules.order")}</TableHead>
              <TableHead>{t("nameRules.matchType")}</TableHead>
              <TableHead>{t("nameRules.pattern")}</TableHead>
              <TableHead>{t("nameRules.target")}</TableHead>
              <TableHead>{t("common.item")}</TableHead>
              <TableHead>{t("common.active")}</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {rules.map((r, i) => {
              const item = items.find((it) => it.id === r.stockItemId);
              return (
                <TableRow key={r.id}>
                  <TableCell>
                    <div className="flex">
                      <Button variant="ghost" size="icon" disabled={i === 0 || reorder.isPending} onClick={() => move(i, -1)} aria-label={t("nameRules.up")}>
                        <ArrowUpIcon />
                      </Button>
                      <Button variant="ghost" size="icon" disabled={i === rules.length - 1 || reorder.isPending} onClick={() => move(i, 1)} aria-label={t("nameRules.down")}>
                        <ArrowDownIcon />
                      </Button>
                    </div>
                  </TableCell>
                  <TableCell>{t(`nameRules.types.${r.matchType}`)}</TableCell>
                  <TableCell className="cursor-pointer font-mono text-xs" onClick={() => setEditing(r)}>
                    {r.pattern}
                  </TableCell>
                  <TableCell className="cursor-pointer font-semibold" onClick={() => setEditing(r)}>
                    {r.targetName}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">{item ? `${lt(item.name, locale)} (${item.sku})` : t("common.none")}</TableCell>
                  <TableCell>
                    <Switch checked={r.active} onCheckedChange={(active) => update.mutate({ rule: r, patch: { active } })} aria-label={t("common.active")} />
                  </TableCell>
                  <TableCell className="text-right">
                    <Button variant="ghost" size="icon" onClick={() => window.confirm(t("common.confirmDelete")) && remove.mutate(r.id)} aria-label={t("common.delete")}>
                      <Trash2Icon />
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
      {editing && <RuleEditor rule={editing === "new" ? undefined : editing} nextPriority={(rules.at(-1)?.priority ?? 0) + 10} onClose={() => setEditing(null)} />}
    </>
  );
}

function RuleEditor({ rule, nextPriority, onClose }: { rule?: ProductNameRule; nextPriority: number; onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const { data: items = [] } = useApi(["stock-items"], () => listStockItems());
  const [form, setForm] = useState<ProductNameRuleRequest>({
    matchType: rule?.matchType ?? "CONTAINS",
    pattern: rule?.pattern ?? "",
    targetName: rule?.targetName ?? "",
    stockItemId: rule?.stockItemId,
    priority: rule?.priority ?? nextPriority,
    active: rule?.active ?? true,
  });
  const save = useApiMutation(() => (rule ? updateProductNameRule({ path: { id: rule.id }, body: form }) : createProductNameRule({ body: form })), {
    invalidate: [["name-rules"]],
    success: t("common.saved"),
    onSuccess: onClose,
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{rule ? t("nameRules.edit") : t("nameRules.new")}</DialogTitle>
        <div className="grid gap-3 sm:grid-cols-[10rem_1fr]">
          <Field label={t("nameRules.matchType")}>
            <NativeSelect value={form.matchType} onChange={(e) => setForm({ ...form, matchType: e.target.value as ProductNameMatchType })}>
              {TYPES.map((m) => (
                <option key={m} value={m}>
                  {t(`nameRules.types.${m}`)}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label={t("nameRules.pattern")} hint={form.matchType === "REGEX" ? t("nameRules.regexHint") : t("nameRules.patternHint")}>
            <Input value={form.pattern} onChange={(e) => setForm({ ...form, pattern: e.target.value })} className="font-mono" />
          </Field>
        </div>
        <Field label={t("nameRules.target")}>
          <Input value={form.targetName} onChange={(e) => setForm({ ...form, targetName: e.target.value })} />
        </Field>
        <Field label={t("nameRules.linkItem")} hint={t("nameRules.linkItemHint")}>
          <NativeSelect value={form.stockItemId ?? ""} onChange={(e) => setForm({ ...form, stockItemId: e.target.value || undefined })}>
            <option value="">{t("common.none")}</option>
            {items.map((it) => (
              <option key={it.id} value={it.id}>
                {lt(it.name, locale)} ({it.sku})
              </option>
            ))}
          </NativeSelect>
        </Field>
        <label className="flex items-center gap-2 text-sm font-medium">
          <Switch checked={form.active ?? true} onCheckedChange={(active) => setForm({ ...form, active })} /> {t("common.active")}
        </label>
        <Button disabled={!form.pattern.trim() || !form.targetName.trim() || save.isPending} onClick={() => save.mutate(undefined)}>
          {t("common.save")}
        </Button>
      </DialogContent>
    </Dialog>
  );
}
