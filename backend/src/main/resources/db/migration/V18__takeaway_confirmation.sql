-- ordering: a customer's to-go order paid at pickup waits for the restaurant (cashier app) before the kitchen.

ALTER TABLE customer_order DROP CONSTRAINT customer_order_status_check;
ALTER TABLE customer_order ADD CONSTRAINT customer_order_status_check
    CHECK (status IN ('PENDING_PAYMENT', 'AWAITING_CONFIRMATION', 'PLACED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED'));

-- settings: how long such an order waits before it is confirmed automatically; 0 = no confirmation.
ALTER TABLE restaurant_settings
    ADD COLUMN takeaway_confirm_minutes INT NOT NULL DEFAULT 5 CHECK (takeaway_confirm_minutes BETWEEN 0 AND 60);
