import {
  confirmGuestRound,
  confirmTableSession,
  decideTableGuest,
  rejectGuestRound,
  rejectTableSession,
  type PendingRound,
  type TableActivity,
  cancelOrder,
  closeTableSession,
  completeOrder,
  mergeTableSession,
  printPreBill,
  transferTableSession,
  unwrap,
  voidTablePayment,
  type FloorTable,
  type TableRound,
  type TableSession,
} from "@amadya/api-client";
import { formatMoney, formatTime } from "@amadya/i18n";
import { hasAnyRole } from "@amadya/staff-auth";
import { Badge, Button, cn, Dialog, DialogContent, DialogDescription, DialogTitle, Input, Sheet, SheetContent, SheetDescription, SheetTitle, Skeleton, toast } from "@amadya/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeftIcon,
  ArrowRightLeftIcon,
  CheckIcon,
  CombineIcon,
  CreditCardIcon,
  DoorOpenIcon,
  EllipsisVerticalIcon,
  PlusIcon,
  PrinterIcon,
  Undo2Icon,
  UsersIcon,
  WalletIcon,
  XIcon,
  QrCodeIcon,
  SmartphoneIcon,
} from "lucide-react";
import { useState } from "react";
import { useLocale, useTranslations } from "use-intl";
import { roundState, sinceLabel, type RoundState } from "../lib/bill";
import { keys, problemMessage, useFloor, useSession, useSessionMutation } from "../lib/data";
import { go } from "../lib/route";
import { useNow } from "./floor";
import { PaymentSheet } from "./payment";

const stateStyle: Record<RoundState, { bg: string; fg: string }> = {
  pending: { bg: "var(--status-pending)", fg: "var(--status-pending-strong)" },
  preparing: { bg: "var(--status-progress)", fg: "var(--status-progress-strong)" },
  ready: { bg: "var(--status-done)", fg: "var(--status-done-strong)" },
  served: { bg: "var(--muted)", fg: "var(--muted-foreground)" },
  cancelled: { bg: "var(--muted)", fg: "var(--muted-foreground)" },
};

export function TableScreen({ sessionId }: { sessionId: string }) {
  const t = useTranslations();
  const locale = useLocale() as "ro" | "en";
  const query = useSession(sessionId);
  const now = useNow(30_000);
  const [paying, setPaying] = useState(false);
  const [actions, setActions] = useState(false);
  const [cancelling, setCancelling] = useState<TableRound | null>(null);

  if (query.isPending) {
    return (
      <div className="space-y-3 p-4">
        <Skeleton className="h-14 rounded-xl" />
        <Skeleton className="h-40 rounded-2xl" />
        <Skeleton className="h-40 rounded-2xl" />
      </div>
    );
  }
  if (query.isError || !query.data) {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-4 p-6 text-center">
        <p className="text-muted-foreground">{problemMessage(query.error)}</p>
        <Button onClick={() => go({ name: "floor" })}>{t("table.backToFloor")}</Button>
      </div>
    );
  }

  const s = query.data;
  const open = s.status === "OPEN";
  const rounds = [...s.rounds].reverse(); // newest first: that is what the waiter acts on

  return (
    <div className="flex min-h-dvh flex-col bg-muted/40">
      <header className="sticky top-0 z-20 flex items-center gap-2 border-b bg-background/95 px-2 pt-[max(0.5rem,env(safe-area-inset-top))] pb-2 backdrop-blur">
        <button onClick={() => go({ name: "floor" })} aria-label={t("table.backToFloor")} className="rounded-lg p-2 hover:bg-muted">
          <ArrowLeftIcon className="size-5" />
        </button>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-lg font-extrabold">
            {t("table.label", { label: s.tableLabel })}
            {s.areaName && <span className="font-medium text-muted-foreground"> · {s.areaName}</span>}
          </h1>
          <p className="flex items-center gap-2 text-xs text-muted-foreground">
            <span className="flex items-center gap-1">
              <UsersIcon className="size-3.5" /> {s.guests}
            </span>
            <span>{sinceLabel(s.openedAt, now)}</span>
            {s.waiterName && <span className="truncate">· {s.waiterName}</span>}
          </p>
        </div>
        {open && (
          <button onClick={() => setActions(true)} aria-label={t("table.actions")} className="rounded-lg p-2 hover:bg-muted">
            <EllipsisVerticalIcon className="size-5" />
          </button>
        )}
      </header>

      {!open && (
        <p role="status" className="m-3 rounded-xl border bg-card p-3 text-sm font-semibold">
          {s.status === "MERGED" ? t("table.merged") : t("table.closed")}
        </p>
      )}

      <main className="flex-1 space-y-3 p-3 pb-44">
        {open && <QrGuests session={s} />}
        {rounds.length === 0 && <p className="py-12 text-center text-muted-foreground">{t("table.noRounds")}</p>}
        {rounds.map((round) => (
          <RoundCard key={round.orderId} round={round} index={s.rounds.indexOf(round) + 1} sessionId={s.id} canAct={open} onCancel={() => setCancelling(round)} />
        ))}
        {s.payments.length > 0 && <Payments session={s} canUndo={open} />}
        {s.activity.length > 0 && <ActivityLog session={s} />}
      </main>

      <footer className="fixed inset-x-0 bottom-0 z-20 border-t bg-background/95 px-3 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] backdrop-blur">
        <dl className="mb-3 grid grid-cols-3 gap-2 text-center text-sm">
          <Amount label={t("bill.total")} value={formatMoney(s.total, locale)} />
          <Amount label={t("bill.paid")} value={formatMoney(s.paid, locale)} />
          <Amount label={t("bill.remaining")} value={formatMoney(s.remaining, locale)} strong />
        </dl>
        {open && (
          <div className="grid grid-cols-2 gap-2">
            <Button size="lg" variant="secondary" onClick={() => go({ name: "order", sessionId: s.id })}>
              <PlusIcon /> {t("table.addRound")}
            </Button>
            <Button size="lg" disabled={Number(s.remaining.amount) <= 0} onClick={() => setPaying(true)}>
              <WalletIcon /> {t("table.pay")}
            </Button>
          </div>
        )}
      </footer>

      {open && <PaymentSheet session={s} open={paying} onClose={() => setPaying(false)} />}
      {open && <ActionsSheet session={s} open={actions} onClose={() => setActions(false)} />}
      <CancelDialog sessionId={s.id} round={cancelling} onClose={() => setCancelling(null)} />
    </div>
  );
}

function Amount({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={cn("tabular-nums", strong ? "text-lg font-extrabold" : "font-semibold")}>{value}</dd>
    </div>
  );
}

function RoundCard({ round, index, sessionId, canAct, onCancel }: { round: TableRound; index: number; sessionId: string; canAct: boolean; onCancel: () => void }) {
  const t = useTranslations();
  const locale = useLocale() as "ro" | "en";
  const state = roundState(round.status);
  const style = stateStyle[state];
  const serve = useOrderAction(sessionId, () => completeOrder({ path: { orderId: round.orderId } }), t("round.served"));
  // Waiters cancel rounds the kitchen has not started; once preparing, a manager decides (the API enforces it too).
  const canCancel = canAct && (state === "pending" || (state === "preparing" && hasAnyRole("MANAGER", "ADMIN")));

  return (
    <article className={cn("overflow-hidden rounded-2xl border bg-card shadow-sm", state === "cancelled" && "opacity-60")}>
      <div className="flex items-center gap-2 px-3 py-2" style={{ background: style.bg }}>
        <span className="font-extrabold">{t("round.title", { index })}</span>
        <span className="font-mono text-sm">{round.number}</span>
        {round.placedBy && <span className="rounded bg-[var(--status-attention)] px-1.5 text-xs font-semibold text-[var(--status-attention-strong)]">QR · {round.placedBy}</span>}
        {round.placedAt && <span className="text-xs">{formatTime(round.placedAt, locale)}</span>}
        <span className="ml-auto rounded-full bg-background/80 px-2 py-0.5 text-xs font-bold uppercase" style={{ color: style.fg }}>
          {t(`round.state.${state}`)}
        </span>
      </div>
      <ul className="divide-y">
        {round.lines.map((l) => (
          <li key={l.lineId} className={cn("flex gap-3 px-3 py-2", state === "cancelled" && "line-through")}>
            <span className="w-7 shrink-0 font-black tabular-nums">{l.quantity}×</span>
            <div className="min-w-0 flex-1">
              <p className="font-semibold">{l.productName}</p>
              {l.modifiers.length > 0 && <p className="text-sm text-muted-foreground">+ {l.modifiers.join(", ")}</p>}
              {l.notes && <p className="text-sm font-semibold text-amber-700">⚠ {l.notes}</p>}
              {l.paidQuantity > 0 && (
                <Badge variant="success" className="mt-1">
                  {t("round.paidUnits", { count: l.paidQuantity })}
                </Badge>
              )}
            </div>
            <span className="text-sm font-semibold tabular-nums">{formatMoney(l.total, locale)}</span>
          </li>
        ))}
      </ul>
      {canAct && (state === "ready" || canCancel) && (
        <div className="flex gap-2 border-t p-2">
          {canCancel && (
            <Button variant="ghost" size="sm" onClick={onCancel}>
              <XIcon /> {t("round.cancel")}
            </Button>
          )}
          {state === "ready" && (
            <Button size="sm" className="ml-auto" disabled={serve.isPending} onClick={() => serve.mutate()}>
              <CheckIcon /> {t("round.markServed")}
            </Button>
          )}
        </div>
      )}
    </article>
  );
}

function Payments({ session, canUndo }: { session: TableSession; canUndo: boolean }) {
  const t = useTranslations();
  const locale = useLocale() as "ro" | "en";
  const [confirming, setConfirming] = useState<string | null>(null);
  const undo = useSessionMutation((paymentId: string) => voidTablePayment({ path: { id: session.id, paymentId } }), () => {
    setConfirming(null);
    toast.success(t("payments.voided"));
  });
  return (
    <section className="rounded-2xl border bg-card p-3 shadow-sm">
      <h2 className="mb-2 font-extrabold">{t("payments.title")}</h2>
      <ul className="space-y-2">
        {session.payments.map((p) => (
          <li key={p.id} className={cn("flex items-center gap-2 text-sm", p.status === "VOIDED" && "text-muted-foreground line-through")}>
            {p.method === "CASH" ? <WalletIcon className="size-4" /> : p.method === "ONLINE" ? <SmartphoneIcon className="size-4" /> : <CreditCardIcon className="size-4" />}
            <span>{t(`payments.method.${p.method}`)}</span>
            <span className="text-xs text-muted-foreground">{formatTime(p.createdAt, locale)}</span>
            {p.itemCount && <span className="text-xs text-muted-foreground">{t("payments.items", { count: p.itemCount })}</span>}
            {p.change && Number(p.change.amount) > 0 && <span className="text-xs text-muted-foreground">{t("payments.change", { amount: formatMoney(p.change, locale) })}</span>}
            <span className="ml-auto font-semibold tabular-nums">{formatMoney(p.amount, locale)}</span>
            {canUndo && p.status === "CAPTURED" && (
              <button onClick={() => setConfirming(p.id)} aria-label={t("payments.undo")} className="rounded p-1 text-muted-foreground hover:bg-muted">
                <Undo2Icon className="size-4" />
              </button>
            )}
          </li>
        ))}
      </ul>
      <Dialog open={!!confirming} onOpenChange={(o) => !o && setConfirming(null)}>
        <DialogContent className="max-w-sm">
          <DialogTitle>{t("payments.undoTitle")}</DialogTitle>
          <DialogDescription>{t("payments.undoText")}</DialogDescription>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setConfirming(null)}>
              {t("common.no")}
            </Button>
            <Button variant="destructive" disabled={undo.isPending} onClick={() => confirming && undo.mutate(confirming)}>
              {t("payments.undo")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </section>
  );
}

function CancelDialog({ sessionId, round, onClose }: { sessionId: string; round: TableRound | null; onClose: () => void }) {
  const t = useTranslations();
  const [reason, setReason] = useState("");
  const cancel = useOrderAction(sessionId, () => cancelOrder({ path: { orderId: round!.orderId }, body: { reason: reason.trim() || t("round.defaultReason") } }), t("round.cancelled"), onClose);
  return (
    <Dialog open={!!round} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogTitle>{round && t("round.cancelTitle", { number: round.number })}</DialogTitle>
        <DialogDescription>{t("round.cancelText")}</DialogDescription>
        <Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder={t("round.reason")} maxLength={200} />
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            {t("common.no")}
          </Button>
          <Button variant="destructive" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
            {t("round.cancel")}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/** Actions on one round (served, cancel): an order endpoint, after which the table is reloaded. */
function useOrderAction(sessionId: string, call: () => Promise<{ data?: unknown; error?: unknown; response?: Response }>, success: string, onDone?: () => void) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => unwrap(call()),
    onSuccess: async () => {
      await Promise.all([client.invalidateQueries({ queryKey: keys.session(sessionId) }), client.invalidateQueries({ queryKey: keys.floor })]);
      toast.success(success);
      onDone?.();
    },
    onError: (e) => toast.error(problemMessage(e)),
  });
}

function ActionsSheet({ session: s, open, onClose }: { session: TableSession; open: boolean; onClose: () => void }) {
  const t = useTranslations();
  const floor = useFloor();
  const [mode, setMode] = useState<"menu" | "transfer" | "merge">("menu");
  const tables = (floor.data ?? []).flatMap((a) => a.tables.map((tb) => ({ ...tb, area: a.name })));
  const free = tables.filter((tb) => !tb.session);
  const others = tables.filter((tb) => tb.session && tb.session.id !== s.id);

  const close = () => {
    setMode("menu");
    onClose();
  };
  const transfer = useSessionMutation((tableId: string) => transferTableSession({ path: { id: s.id }, body: { tableId } }), (updated) => {
    toast.success(t("actions.transferred", { label: updated.tableLabel }));
    close();
  });
  const merge = useSessionMutation((targetSessionId: string) => mergeTableSession({ path: { id: s.id }, body: { targetSessionId } }), (target) => {
    toast.success(t("actions.merged", { label: target.tableLabel }));
    close();
    go({ name: "table", sessionId: target.id });
  });
  const freeTable = useSessionMutation(() => closeTableSession({ path: { id: s.id } }), () => {
    toast.success(t("actions.freed"));
    close();
    go({ name: "floor" });
  });
  const preBill = useMutation({
    mutationFn: () => unwrap(printPreBill({ path: { id: s.id } })),
    onSuccess: () => {
      toast.success(t("actions.preBillSent"));
      close();
    },
    onError: (e) => toast.error(problemMessage(e)),
  });

  const nothingOwed = Number(s.remaining.amount) <= 0;

  return (
    <Sheet open={open} onOpenChange={(o) => !o && close()}>
      <SheetContent side="bottom" className="max-h-[85dvh] overflow-y-auto pb-[max(1rem,env(safe-area-inset-bottom))]">
        <SheetTitle>{t("table.label", { label: s.tableLabel })}</SheetTitle>
        {mode === "menu" && (
          <div className="grid gap-2 pt-2">
            <ActionButton icon={<PrinterIcon />} label={t("actions.preBill")} hint={t("actions.preBillHint")} onClick={() => preBill.mutate()} disabled={preBill.isPending || s.rounds.length === 0} />
            <ActionButton icon={<ArrowRightLeftIcon />} label={t("actions.transfer")} hint={t("actions.transferHint")} onClick={() => setMode("transfer")} />
            <ActionButton icon={<CombineIcon />} label={t("actions.merge")} hint={t("actions.mergeHint")} onClick={() => setMode("merge")} />
            <ActionButton
              icon={<DoorOpenIcon />}
              label={t("actions.free")}
              hint={nothingOwed ? t("actions.freeHint") : t("actions.freeBlocked")}
              onClick={() => freeTable.mutate(undefined)}
              disabled={!nothingOwed || freeTable.isPending}
            />
          </div>
        )}
        {mode !== "menu" && (
          <>
            <SheetDescription>{mode === "transfer" ? t("actions.pickFree") : t("actions.pickOccupied")}</SheetDescription>
            <TablePicker
              tables={mode === "transfer" ? free : others}
              empty={mode === "transfer" ? t("actions.noFree") : t("actions.noOccupied")}
              busy={transfer.isPending || merge.isPending}
              onPick={(tb) => (mode === "transfer" ? transfer.mutate(tb.id) : merge.mutate(tb.session!.id))}
            />
            <Button variant="ghost" className="mt-2 w-full" onClick={() => setMode("menu")}>
              {t("common.back")}
            </Button>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

function ActionButton({ icon, label, hint, onClick, disabled }: { icon: React.ReactNode; label: string; hint: string; onClick: () => void; disabled?: boolean }) {
  return (
    <button onClick={onClick} disabled={disabled} className="flex items-center gap-3 rounded-xl border bg-card p-3 text-left transition hover:bg-muted disabled:opacity-50 [&_svg]:size-5">
      {icon}
      <span className="min-w-0">
        <span className="block font-semibold">{label}</span>
        <span className="block text-xs text-muted-foreground">{hint}</span>
      </span>
    </button>
  );
}

function TablePicker({ tables, empty, busy, onPick }: { tables: (FloorTable & { area: string })[]; empty: string; busy: boolean; onPick: (t: FloorTable) => void }) {
  if (tables.length === 0) return <p className="py-6 text-center text-sm text-muted-foreground">{empty}</p>;
  return (
    <ul className="grid grid-cols-3 gap-2 pt-2 sm:grid-cols-4">
      {tables.map((tb) => (
        <li key={tb.id}>
          <button disabled={busy} onClick={() => onPick(tb)} className="flex h-16 w-full flex-col items-center justify-center rounded-xl border bg-card font-black disabled:opacity-50">
            <span className="text-xl">{tb.label}</span>
            <span className="text-[11px] font-medium text-muted-foreground">{tb.area}</span>
          </button>
        </li>
      ))}
    </ul>
  );
}

/** Guests from the QR code: confirm their table and rounds before the time runs out, let people in, remove them. */
function QrGuests({ session: s }: { session: TableSession }) {
  const t = useTranslations();
  const locale = useLocale() as "ro" | "en";
  const now = useNow(1000);
  const [refusing, setRefusing] = useState<{ round?: PendingRound } | null>(null);
  const [reason, setReason] = useState("");
  const confirmTable = useSessionMutation(() => confirmTableSession({ path: { id: s.id } }), () => toast.success(t("qr.tableConfirmed")));
  const confirmRound = useSessionMutation((roundId: string) => confirmGuestRound({ path: { id: s.id, roundId } }), () => toast.success(t("qr.roundConfirmed")));
  const refuse = useSessionMutation(
    () =>
      refusing?.round
        ? rejectGuestRound({ path: { id: s.id, roundId: refusing.round.id }, body: { reason: reason.trim() || undefined } })
        : rejectTableSession({ path: { id: s.id }, body: { reason: reason.trim() || undefined } }),
    (updated) => {
      setRefusing(null);
      setReason("");
      if (updated.status !== "OPEN") go({ name: "floor" });
    },
  );
  const decide = useSessionMutation(({ guestId, action }: { guestId: string; action: "approve" | "reject" | "remove" }) => decideTableGuest({ path: { id: s.id, guestId, action } }));
  const left = (iso: string) => {
    const sec = Math.max(0, Math.round((Date.parse(iso) - now) / 1000));
    return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, "0")}`;
  };
  const guests = s.guestList.filter((g) => g.status === "ACTIVE" || g.status === "PENDING");
  if (!s.awaitingConfirmation && s.pendingRounds.length === 0 && guests.length === 0) return null;

  return (
    <section className="space-y-3 rounded-2xl border-2 border-[var(--status-attention-strong)] bg-[var(--status-attention)] p-3">
      <h2 className="flex items-center gap-2 font-extrabold text-[var(--status-attention-strong)]">
        <QrCodeIcon className="size-5" /> {t("qr.title")}
      </h2>

      {s.awaitingConfirmation && (
        <div className="space-y-2 rounded-xl bg-card p-3">
          <p className="text-sm font-semibold">{t("qr.newTable")}</p>
          {s.confirmDeadline && <p className="text-xs text-muted-foreground">{t("qr.autoIn", { time: left(s.confirmDeadline) })}</p>}
          <div className="flex gap-2">
            <Button variant="ghost" size="sm" onClick={() => setRefusing({})}>
              <XIcon /> {t("qr.refuseTable")}
            </Button>
            <Button size="sm" className="ml-auto" disabled={confirmTable.isPending} onClick={() => confirmTable.mutate(undefined)}>
              <CheckIcon /> {t("qr.confirmTable")}
            </Button>
          </div>
        </div>
      )}

      {s.pendingRounds.map((r) => (
        <article key={r.id} className="space-y-2 rounded-xl bg-card p-3">
          <p className="flex items-center justify-between gap-2 text-sm font-bold">
            <span>{t("qr.roundFrom", { name: r.guestName ?? "?" })}</span>
            <span className="font-mono text-xs text-muted-foreground tabular-nums">{t("qr.autoIn", { time: left(r.deadline) })}</span>
          </p>
          <ul className="space-y-0.5 text-sm">
            {r.lines.map((l, i) => (
              <li key={i}>
                <span className="font-black">{l.quantity}×</span> {l.productName}
                {l.modifiers.length > 0 && <span className="text-muted-foreground"> + {l.modifiers.join(", ")}</span>}
                {l.notes && <span className="block text-xs font-semibold text-amber-700">⚠ {l.notes}</span>}
              </li>
            ))}
          </ul>
          {r.notes && <p className="text-xs font-semibold text-amber-700">⚠ {r.notes}</p>}
          <div className="flex flex-wrap items-center gap-2">
            <span className="mr-auto text-sm font-semibold tabular-nums">{formatMoney(r.total, locale)}</span>
            <Button variant="ghost" size="sm" onClick={() => setRefusing({ round: r })}>
              <XIcon /> {t("qr.refuse")}
            </Button>
            <Button size="sm" disabled={confirmRound.isPending} onClick={() => confirmRound.mutate(r.id)}>
              <CheckIcon /> {t("qr.sendToKitchen")}
            </Button>
          </div>
        </article>
      ))}

      {guests.length > 0 && (
        <ul className="divide-y rounded-xl bg-card">
          {guests.map((g) => (
            <li key={g.id} className="flex items-center gap-2 p-2 pl-3 text-sm">
              <span className="min-w-0 flex-1">
                <span className="font-semibold">{g.name}</span>
                <span className="text-xs text-muted-foreground"> · {t(g.status === "PENDING" ? "qr.asking" : g.role === "OWNER" ? "qr.owner" : "qr.member")}</span>
              </span>
              {g.status === "PENDING" ? (
                <>
                  <Button variant="ghost" size="sm" disabled={decide.isPending} onClick={() => decide.mutate({ guestId: g.id, action: "reject" })}>
                    {t("qr.refuse")}
                  </Button>
                  <Button size="sm" disabled={decide.isPending} onClick={() => decide.mutate({ guestId: g.id, action: "approve" })}>
                    {t("qr.letIn")}
                  </Button>
                </>
              ) : (
                <Button variant="ghost" size="sm" disabled={decide.isPending} onClick={() => decide.mutate({ guestId: g.id, action: "remove" })}>
                  {t("qr.remove")}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      <Dialog open={!!refusing} onOpenChange={(o) => !o && setRefusing(null)}>
        <DialogContent className="max-w-sm">
          <DialogTitle>{refusing?.round ? t("qr.refuseRoundTitle") : t("qr.refuseTableTitle")}</DialogTitle>
          <DialogDescription>{refusing?.round ? t("qr.refuseRoundText") : t("qr.refuseTableText")}</DialogDescription>
          <Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder={t("round.reason")} maxLength={200} />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setRefusing(null)}>
              {t("common.no")}
            </Button>
            <Button variant="destructive" disabled={refuse.isPending} onClick={() => refuse.mutate(undefined)}>
              {t("qr.refuse")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </section>
  );
}

function ActivityLog({ session: s }: { session: TableSession }) {
  const t = useTranslations();
  const locale = useLocale() as "ro" | "en";
  const text = (a: TableActivity) => {
    const items = (a.items ?? []).map((i) => `${i.quantity}× ${i.name}`).join(", ");
    const actor = a.actor ?? t("activity.system");
    const amount = a.amount ? formatMoney(a.amount, locale) : "";
    return t(`activity.${a.kind}`, { actor, items, amount, method: a.method ? t(`payments.method.${a.method}`) : "", auto: a.automatic ? t("activity.auto") : "", reason: a.reason ?? "" });
  };
  return (
    <details className="rounded-2xl border bg-card p-3 shadow-sm">
      <summary className="cursor-pointer font-extrabold">{t("activity.title")}</summary>
      <ol className="mt-2 space-y-1.5">
        {[...s.activity].reverse().map((a, i) => (
          <li key={i} className="flex gap-2 text-sm">
            <time className="w-11 shrink-0 text-xs text-muted-foreground tabular-nums">{formatTime(a.at, locale)}</time>
            <span>{text(a)}</span>
          </li>
        ))}
      </ol>
    </details>
  );
}
