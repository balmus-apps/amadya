import type { MenuModifierGroup, MenuModifierOption } from "./types";

/** Selected option ids per modifier group. */
export type Selection = Record<string, string[]>;

/** Required single-choice groups start with their first option selected (e.g. "Choose your sauce"). */
export function initialSelection(groups: MenuModifierGroup[]): Selection {
  return Object.fromEntries(groups.filter((g) => g.minSelect === 1 && g.maxSelect === 1 && g.options[0]).map((g) => [g.id, [g.options[0]!.id]]));
}

/** Applies a tap on an option, respecting the group's max. */
export function toggleOption(selection: Selection, group: MenuModifierGroup, optionId: string, checked: boolean): Selection {
  const current = selection[group.id] ?? [];
  if (group.maxSelect === 1) return { ...selection, [group.id]: checked ? [optionId] : [] };
  const next = checked ? [...new Set([...current, optionId])] : current.filter((id) => id !== optionId);
  return next.length > group.maxSelect ? selection : { ...selection, [group.id]: next };
}

export function chosenOptions(groups: MenuModifierGroup[], selection: Selection): MenuModifierOption[] {
  return groups.flatMap((g) => g.options.filter((o) => selection[g.id]?.includes(o.id)));
}

/** Groups whose minimum is not met yet. */
export function missingGroups(groups: MenuModifierGroup[], selection: Selection): MenuModifierGroup[] {
  return groups.filter((g) => (selection[g.id]?.length ?? 0) < g.minSelect);
}
