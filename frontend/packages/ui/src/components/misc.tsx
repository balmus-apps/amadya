"use client";

import { MinusIcon, PlusIcon } from "lucide-react";
import { Separator as SeparatorPrimitive } from "radix-ui";
import * as React from "react";
import { Toaster as Sonner, type ToasterProps } from "sonner";
import { cn } from "../lib/utils";

function Separator({ className, orientation = "horizontal", ...props }: React.ComponentProps<typeof SeparatorPrimitive.Root>) {
  return (
    <SeparatorPrimitive.Root
      data-slot="separator"
      orientation={orientation}
      className={cn("shrink-0 bg-border data-[orientation=horizontal]:h-px data-[orientation=horizontal]:w-full data-[orientation=vertical]:h-full data-[orientation=vertical]:w-px", className)}
      {...props}
    />
  );
}

function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return <div data-slot="skeleton" className={cn("animate-pulse rounded-lg bg-muted", className)} {...props} />;
}

function Toaster(props: ToasterProps) {
  return (
    <Sonner
      position="top-center"
      toastOptions={{ classNames: { toast: "!rounded-xl !border !bg-card !text-card-foreground", error: "!text-destructive" } }}
      {...props}
    />
  );
}

/** −  2  + control with large touch targets. */
function QuantityStepper({
  value,
  onChange,
  min = 1,
  max = 99,
  size = "default",
  decreaseLabel = "Decrease",
  increaseLabel = "Increase",
  className,
}: {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  size?: "default" | "sm";
  decreaseLabel?: string;
  increaseLabel?: string;
  className?: string;
}) {
  const button = cn(
    "flex items-center justify-center rounded-full border bg-card transition hover:bg-muted active:scale-95 disabled:opacity-40",
    size === "sm" ? "size-8" : "size-10",
  );
  return (
    <div className={cn("inline-flex items-center gap-3", className)}>
      <button type="button" className={button} onClick={() => onChange(value - 1)} disabled={value <= min} aria-label={decreaseLabel}>
        <MinusIcon className="size-4" />
      </button>
      <span className={cn("min-w-6 text-center font-semibold tabular-nums", size === "sm" ? "text-sm" : "text-lg")} aria-live="polite">
        {value}
      </span>
      <button type="button" className={button} onClick={() => onChange(value + 1)} disabled={value >= max} aria-label={increaseLabel}>
        <PlusIcon className="size-4" />
      </button>
    </div>
  );
}

export { QuantityStepper, Separator, Skeleton, Toaster };
export { toast } from "sonner";
