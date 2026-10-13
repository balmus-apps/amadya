-- settings module: online payment for customer orders is off until the restaurant has a payment provider account.
-- Customers then pay at pickup; admins can still turn it on (e.g. a demo with the simulated provider).

UPDATE restaurant_settings
SET features = features || '{"onlinePayments": false}';
