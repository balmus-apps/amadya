-- DEV ONLY: a bar station with drinks prepared to order, for the kitchen & bar display.
INSERT INTO station (id, code, name, parallel_slots)
VALUES ('0190a000-0000-7000-8000-000000000103', 'BAR', '{"ro":"Bar","en":"Bar"}', 2);

INSERT INTO product (id, category_id, name, description, price, vat_rate_id, station_id, kind, prep_time_sec, sort_order, allergens)
VALUES
  ('0190a000-0000-7000-8000-000000000461', '0190a000-0000-7000-8000-000000000206',
   '{"ro":"Limonadă cu mentă","en":"Mint lemonade"}', '{"ro":"Lămâie proaspătă, mentă, miere","en":"Fresh lemon, mint, honey"}',
   14.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 180, 3, '{}'),
  ('0190a000-0000-7000-8000-000000000462', '0190a000-0000-7000-8000-000000000206',
   '{"ro":"Espresso","en":"Espresso"}', NULL,
   9.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 90, 4, '{}'),
  ('0190a000-0000-7000-8000-000000000463', '0190a000-0000-7000-8000-000000000206',
   '{"ro":"Frappé","en":"Frappé"}', '{"ro":"Cafea rece cu lapte și gheață","en":"Iced coffee with milk"}',
   16.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 240, 5, '{lapte}');
