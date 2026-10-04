// Structural copies of the API menu types, so this package does not depend on the generated client.
export interface Money {
  amount: string;
  currency: string;
}

export interface MenuModifierOption {
  id: string;
  name: string;
  priceDelta: Money;
}

export interface MenuModifierGroup {
  id: string;
  name: string;
  minSelect: number;
  maxSelect: number;
  options: MenuModifierOption[];
}
