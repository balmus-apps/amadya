/**
 * Touch UI shared by the staff apps that take orders and money (waiter, cashier): the menu picker and the
 * Cash / Card panel. Strings live under the `pos` namespace; each app merges `posMessages[locale]` into its messages.
 */
export { MenuFilters, OptionsSheet, ProductGrid, useProductFilter } from "./menu-picker";
export { PaymentFields, paymentValid, quickNotes, Segmented, type PaymentChoice } from "./payment-panel";
export { posMessages } from "./messages";
