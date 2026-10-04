import { login, unwrap } from "@amadya/api-client";
import { Button, Card, CardContent, Input } from "@amadya/ui";
import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { Field } from "@/components/kit";
import { useT } from "@/lib/i18n";
import { session, signIn, signOut } from "@/lib/session";

export function LoginPage() {
  const t = useT();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(undefined);
    try {
      signIn(await unwrap(login({ body: { email, password } })));
      const roles = session.getState().claims?.roles ?? [];
      if (!roles.some((r) => r === "ADMIN" || r === "MANAGER")) {
        signOut();
        setError(t("login.notAllowed"));
        return;
      }
      await navigate({ to: "/" });
    } catch {
      setError(t("login.error"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-sm">
        <CardContent className="space-y-5 p-6">
          <div className="flex items-center gap-2">
            <span className="flex size-9 items-center justify-center rounded-lg bg-primary font-extrabold text-primary-foreground">A</span>
            <h1 className="text-xl font-extrabold">{t("login.title")}</h1>
          </div>
          <form onSubmit={submit} className="space-y-4">
            <Field label={t("login.email")} htmlFor="email">
              <Input id="email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </Field>
            <Field label={t("login.password")} htmlFor="password">
              <Input id="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </Field>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button type="submit" className="w-full" disabled={busy}>
              {t("login.submit")}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
