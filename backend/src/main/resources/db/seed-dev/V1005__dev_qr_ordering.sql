-- DEV ONLY: QR ordering on, with readable codes for the demo tables (production codes are random).
UPDATE restaurant_settings SET features = features || '{"qrOrdering": true}'::jsonb;
UPDATE dining_table SET qr_code = 'demo-' || lower(label) WHERE id::text LIKE '0190a000-0000-7000-8000-0000000007%';
