-- kitchen module: who the ticket is for, shown on the kitchen / bar display.
ALTER TABLE kitchen_ticket ADD COLUMN customer_name TEXT;
ALTER TABLE kitchen_ticket ADD COLUMN table_label TEXT;
