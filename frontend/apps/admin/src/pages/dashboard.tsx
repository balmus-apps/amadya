import { getDashboard } from "@amadya/api-client";
import { Badge, Card, CardContent, CardHeader, CardTitle, cn, Skeleton, Table, TableBody, TableCell, TableRow } from "@amadya/ui";
import { Link } from "@tanstack/react-router";
import { AlertTriangleIcon } from "lucide-react";
import { useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { PageHeader } from "@/components/kit";
import { useApi } from "@/lib/data";
import { daysAgo, formatMoney, today } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

const ranges = { today: 0, last7: 6, last30: 29 } as const;

export function DashboardPage() {
  const t = useT();
  const { locale } = useLocale();
  const [range, setRange] = useState<keyof typeof ranges>("last7");
  const from = daysAgo(ranges[range]);
  const to = today();
  const { data } = useApi(["dashboard", from, to], () => getDashboard({ query: { from, to } }), { refetchInterval: 60_000 });

  const kpi = (label: string, value: string | undefined, sub?: string) => (
    <Card>
      <CardContent className="p-5">
        <p className="text-sm text-muted-foreground">{label}</p>
        {value === undefined ? <Skeleton className="mt-2 h-8 w-28" /> : <p className="mt-1 text-2xl font-extrabold tabular-nums">{value}</p>}
        {sub && <p className="text-xs text-muted-foreground">{sub}</p>}
      </CardContent>
    </Card>
  );
  const revenue = data ? Number(data.revenue.amount) : 0;
  const foodCostPct = data?.foodCost && revenue > 0 ? Math.round((Number(data.foodCost.amount) / revenue) * 100) : undefined;

  return (
    <>
      <PageHeader
        title={t("dashboard.title")}
        actions={
          <div className="flex rounded-lg border bg-card p-1">
            {(Object.keys(ranges) as (keyof typeof ranges)[]).map((r) => (
              <button key={r} onClick={() => setRange(r)} className={cn("rounded-md px-3 py-1.5 text-sm font-medium", r === range ? "bg-primary text-primary-foreground" : "text-muted-foreground")}>
                {t(`dashboard.${r}`)}
              </button>
            ))}
          </div>
        }
      />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {kpi(t("dashboard.revenue"), data && formatMoney(data.revenue, locale), data?.vat ? `TVA ${formatMoney(data.vat, locale)}` : undefined)}
        {kpi(t("dashboard.orders"), data?.orders.toString(), data ? `${t("dashboard.cancelled")}: ${data.cancelled}` : undefined)}
        {kpi(t("dashboard.averageTicket"), data && formatMoney(data.averageTicket, locale))}
        {kpi(
          t("dashboard.foodCost"),
          data?.foodCost && formatMoney(data.foodCost, locale),
          foodCostPct !== undefined ? t("dashboard.foodCostShare", { value: foodCostPct }) : undefined,
        )}
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>{t("dashboard.byDay")}</CardTitle>
          </CardHeader>
          <CardContent className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={(data?.byDay ?? []).map((d) => ({ day: d.date.slice(5), revenue: Number(d.revenue.amount) }))}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border)" />
                <XAxis dataKey="day" tickLine={false} axisLine={false} fontSize={12} />
                <YAxis tickLine={false} axisLine={false} fontSize={12} width={48} />
                <Tooltip formatter={(v) => formatMoney({ amount: String(v), currency: data?.revenue.currency ?? "RON" }, locale)} />
                <Bar dataKey="revenue" fill="var(--primary)" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
        <div className="grid gap-4">
          {kpi(t("dashboard.prepTime"), data ? (data.averagePrepSeconds != null ? t("dashboard.minutes", { value: Math.round(data.averagePrepSeconds / 60) }) : "—") : undefined)}
          {kpi(t("dashboard.stockValue"), data && formatMoney(data.stockValue, locale))}
          <Link to="/stock" search={{ low: true }}>
            <Card className={cn("transition hover:shadow-md", data && data.lowStockCount > 0 && "border-accent")}>
              <CardContent className="flex items-center gap-3 p-5">
                <AlertTriangleIcon className={cn("size-6", data && data.lowStockCount > 0 ? "text-accent" : "text-muted-foreground")} />
                <div>
                  <p className="text-sm text-muted-foreground">{t("dashboard.lowStock")}</p>
                  <p className="text-2xl font-extrabold">{data?.lowStockCount ?? "…"}</p>
                </div>
              </CardContent>
            </Card>
          </Link>
        </div>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>{t("dashboard.topProducts")}</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableBody>
                {(data?.topProducts ?? []).map((p, i) => (
                  <TableRow key={p.productId}>
                    <TableCell className="w-8 text-muted-foreground">{i + 1}</TableCell>
                    <TableCell className="font-medium">{p.name}</TableCell>
                    <TableCell className="text-right tabular-nums">{p.quantity}×</TableCell>
                    <TableCell className="text-right tabular-nums">{formatMoney(p.revenue, locale)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("dashboard.byHour")}</CardTitle>
          </CardHeader>
          <CardContent className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={Array.from({ length: 24 }, (_, h) => ({ h, orders: data?.byHour.find((x) => x.hour === h)?.orders ?? 0 })).filter((x) => x.h >= 8)}>
                <XAxis dataKey="h" tickLine={false} axisLine={false} fontSize={11} />
                <Tooltip />
                <Bar dataKey="orders" fill="var(--accent)" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
            <div className="mt-2 flex flex-wrap gap-2">
              {(data?.byChannel ?? []).map((c) => (
                <Badge key={c.channel} variant="outline">
                  {t(`orders.channels.${c.channel}`)}: {c.orders}
                </Badge>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
