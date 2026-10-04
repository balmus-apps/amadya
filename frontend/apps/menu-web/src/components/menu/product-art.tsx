import { cn } from "@amadya/ui";
import { CupSodaIcon, DrumstickIcon, HamburgerIcon, SandwichIcon, SoupIcon, UtensilsIcon, type LucideIcon } from "lucide-react";

const icons: [RegExp, LucideIcon][] = [
  [/burger/i, HamburgerIcon],
  [/lipie|wrap|sandv|sandwich|burrito|pita/i, SandwichIcon],
  [/cola|ap[aă]|water|suc|juice|bere|beer|drink|b[aă]utur/i, CupSodaIcon],
  [/fasole|bean|ciorb|sup[aă]|soup/i, SoupIcon],
  [/platou|platter|gr[aă]tar|grill|mici|pui|chicken/i, DrumstickIcon],
];

/** Product photo, or a branded illustration when the restaurant has not uploaded one yet. */
export function ProductArt({ name, imageUrl, className, iconClassName }: { name: string; imageUrl?: string; className?: string; iconClassName?: string }) {
  if (imageUrl) {
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={imageUrl} alt="" className={cn("object-cover", className)} loading="lazy" />;
  }
  const Icon = icons.find(([pattern]) => pattern.test(name))?.[1] ?? UtensilsIcon;
  return (
    <div
      aria-hidden
      className={cn("flex items-center justify-center bg-gradient-to-br from-primary/90 via-primary/70 to-accent/80 text-primary-foreground", className)}
    >
      <Icon className={cn("size-10 drop-shadow-sm", iconClassName)} strokeWidth={1.6} />
    </div>
  );
}
