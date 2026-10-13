import { createCartStore } from "@amadya/customer-core";

/** The order being taken at the counter; kept for the browser tab so a reload does not lose it. */
export const ticket = createCartStore(() => sessionStorage);
