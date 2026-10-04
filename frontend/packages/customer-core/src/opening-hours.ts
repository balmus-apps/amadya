export interface OpeningHours {
  dayOfWeek: number;
  opens: string;
  closes: string;
}

/** Day of week (1 = Monday) and "HH:mm" in the restaurant's time zone. */
export function localClock(now: Date, timeZone = "Europe/Bucharest") {
  const parts = new Intl.DateTimeFormat("en-GB", { timeZone, weekday: "short", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).formatToParts(now);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  const day = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].indexOf(get("weekday")) + 1;
  return { day, time: `${get("hour")}:${get("minute")}` };
}

/** True when there is no schedule (always open) or now falls inside today's hours; supports closing after midnight. */
export function isOpenNow(hours: OpeningHours[], now = new Date(), timeZone?: string): boolean {
  if (hours.length === 0) return true;
  const { day, time } = localClock(now, timeZone);
  const yesterday = day === 1 ? 7 : day - 1;
  return hours.some((h) => {
    if (h.closes > h.opens) return h.dayOfWeek === day && time >= h.opens && time < h.closes;
    // e.g. 18:00–02:00
    return (h.dayOfWeek === day && time >= h.opens) || (h.dayOfWeek === yesterday && time < h.closes);
  });
}

export function todaysHours(hours: OpeningHours[], now = new Date(), timeZone?: string): OpeningHours | undefined {
  const { day } = localClock(now, timeZone);
  return hours.find((h) => h.dayOfWeek === day);
}
