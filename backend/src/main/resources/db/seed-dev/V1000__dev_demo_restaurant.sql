-- DEV ONLY (loaded with the `dev` profile): demo restaurant based on the reference images in burregescu-img/.
-- Production installs start empty and are configured from the admin dashboard.

UPDATE restaurant_settings
SET name          = 'BurRegescu',
    legal_name    = 'BurRegescu Fast Food (demo)',
    address       = 'Strada Principală 109, Șendreni',
    phone         = '+40756501239',
    theme         = '{"primary":"#FFC400","onPrimary":"#111111","secondary":"#111111","accent":"#D62828","background":"#FFFBEB","foreground":"#111111","radius":"14px","fontHeading":"Rubik","fontBody":"Inter"}',
    opening_hours = '[{"dayOfWeek":1,"opens":"11:00","closes":"22:00"},{"dayOfWeek":2,"opens":"11:00","closes":"22:00"},{"dayOfWeek":3,"opens":"11:00","closes":"22:00"},{"dayOfWeek":4,"opens":"11:00","closes":"22:00"},{"dayOfWeek":5,"opens":"11:00","closes":"23:00"},{"dayOfWeek":6,"opens":"12:00","closes":"23:00"},{"dayOfWeek":7,"opens":"12:00","closes":"22:00"}]',
    order_number_prefix = 'B'
WHERE id = 1;

INSERT INTO station (id, code, name, parallel_slots)
VALUES ('0190a000-0000-7000-8000-000000000102', 'GRILL', '{"ro":"Grătar","en":"Grill"}', 3);

INSERT INTO category (id, name, sort_order)
VALUES ('0190a000-0000-7000-8000-000000000201', '{"ro":"Burgeri","en":"Burgers"}', 10),
       ('0190a000-0000-7000-8000-000000000202', '{"ro":"Lipii și sandvișuri","en":"Wraps & sandwiches"}', 20),
       ('0190a000-0000-7000-8000-000000000203', '{"ro":"Platouri","en":"Platters"}', 30),
       ('0190a000-0000-7000-8000-000000000204', '{"ro":"Mâncare gătită","en":"Home-style dishes"}', 40),
       ('0190a000-0000-7000-8000-000000000205', '{"ro":"Garnituri","en":"Sides"}', 50),
       ('0190a000-0000-7000-8000-000000000206', '{"ro":"Băuturi","en":"Drinks"}', 60);

-- Modifier groups
INSERT INTO modifier_group (id, name, min_select, max_select)
VALUES ('0190a000-0000-7000-8000-000000000301', '{"ro":"Alege sosul","en":"Choose your sauce"}', 1, 1),
       ('0190a000-0000-7000-8000-000000000302', '{"ro":"Extra","en":"Extras"}', 0, 3);

INSERT INTO modifier_option (id, group_id, name, price_delta, sort_order)
VALUES ('0190a000-0000-7000-8000-000000000311', '0190a000-0000-7000-8000-000000000301', '{"ro":"Sos cheese","en":"Cheese sauce"}', 0, 1),
       ('0190a000-0000-7000-8000-000000000312', '0190a000-0000-7000-8000-000000000301', '{"ro":"Sos usturoi","en":"Garlic sauce"}', 0, 2),
       ('0190a000-0000-7000-8000-000000000313', '0190a000-0000-7000-8000-000000000301', '{"ro":"Sos picant","en":"Hot sauce"}', 0, 3),
       ('0190a000-0000-7000-8000-000000000321', '0190a000-0000-7000-8000-000000000302', '{"ro":"Bacon crocant","en":"Crispy bacon"}', 5, 1),
       ('0190a000-0000-7000-8000-000000000322', '0190a000-0000-7000-8000-000000000302', '{"ro":"Cheddar extra","en":"Extra cheddar"}', 4, 2),
       ('0190a000-0000-7000-8000-000000000323', '0190a000-0000-7000-8000-000000000302', '{"ro":"Ou","en":"Egg"}', 3, 3);

-- Products (food at the reduced 11% rate, soft drinks at 21%)
INSERT INTO product (id, category_id, name, description, price, vat_rate_id, station_id, kind, prep_time_sec, sort_order, allergens)
VALUES
  ('0190a000-0000-7000-8000-000000000401', '0190a000-0000-7000-8000-000000000201',
   '{"ro":"Burger clasic","en":"Classic burger"}',
   '{"ro":"Chiflă pufoasă, carne de vită suculentă, cheddar topit, bacon, sosul casei","en":"Soft bun, juicy beef patty, melted cheddar, bacon, house sauce"}',
   32.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 540, 1, '{gluten,lapte,ou}'),
  ('0190a000-0000-7000-8000-000000000402', '0190a000-0000-7000-8000-000000000201',
   '{"ro":"Burger dublu","en":"Double burger"}',
   '{"ro":"Două chifteluțe de vită, dublu cheddar, ceapă caramelizată, salată de varză","en":"Two beef patties, double cheddar, caramelised onion, coleslaw"}',
   42.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 660, 2, '{gluten,lapte,ou}'),
  ('0190a000-0000-7000-8000-000000000411', '0190a000-0000-7000-8000-000000000202',
   '{"ro":"Lipie cu pui","en":"Chicken pita"}',
   '{"ro":"Piept de pui la grătar, cartofi, roșii, castraveți — alege sos cheese sau usturoi","en":"Grilled chicken breast, fries, tomato, cucumber — choose cheese or garlic sauce"}',
   20.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 420, 1, '{gluten,lapte}'),
  ('0190a000-0000-7000-8000-000000000412', '0190a000-0000-7000-8000-000000000202',
   '{"ro":"Sandviș cu mici","en":"Mici sandwich"}',
   '{"ro":"Mici la grătar în baghetă, salată de varză și sos picant","en":"Grilled mici in a baguette with coleslaw and hot sauce"}',
   22.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 480, 2, '{gluten}'),
  ('0190a000-0000-7000-8000-000000000413', '0190a000-0000-7000-8000-000000000202',
   '{"ro":"Burrito gratinat","en":"Cheese-topped burrito"}',
   '{"ro":"Tortilla umplută cu pui și legume, gratinată cu cheddar","en":"Tortilla filled with chicken and vegetables, baked with cheddar"}',
   28.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 600, 3, '{gluten,lapte}'),
  ('0190a000-0000-7000-8000-000000000421', '0190a000-0000-7000-8000-000000000203',
   '{"ro":"Platou burgeri","en":"Burger platter"}',
   '{"ro":"4 burgeri cu cheddar și bacon, cartofi prăjiți și sosuri — perfect pentru 4 persoane","en":"4 cheddar & bacon burgers, fries and sauces — perfect for 4"}',
   140.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 1200, 1, '{gluten,lapte,ou}'),
  ('0190a000-0000-7000-8000-000000000422', '0190a000-0000-7000-8000-000000000203',
   '{"ro":"Platou grătar","en":"Grill platter"}',
   '{"ro":"Mici, ceafă de porc, pulpe de pui, cârnați și cartofi cu parmezan","en":"Mici, pork neck, chicken thighs, sausages and parmesan fries"}',
   180.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 1500, 2, '{lapte}'),
  ('0190a000-0000-7000-8000-000000000431', '0190a000-0000-7000-8000-000000000204',
   '{"ro":"Fasole cu afumătură","en":"Beans with smoked pork"}',
   '{"ro":"Un prânz delicios și sățios, servit cu pâine","en":"A hearty Romanian lunch, served with bread"}',
   25.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 240, 1, '{gluten}'),
  ('0190a000-0000-7000-8000-000000000441', '0190a000-0000-7000-8000-000000000205',
   '{"ro":"Cartofi prăjiți","en":"French fries"}', NULL,
   12.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 300, 1, '{}'),
  ('0190a000-0000-7000-8000-000000000451', '0190a000-0000-7000-8000-000000000206',
   '{"ro":"Coca-Cola 330 ml","en":"Coca-Cola 330 ml"}', NULL,
   8.00, '0190a000-0000-7000-8000-000000000001', NULL, 'RESALE', 0, 1, '{}'),
  ('0190a000-0000-7000-8000-000000000452', '0190a000-0000-7000-8000-000000000206',
   '{"ro":"Apă plată 500 ml","en":"Still water 500 ml"}', NULL,
   6.00, '0190a000-0000-7000-8000-000000000001', NULL, 'RESALE', 0, 2, '{}');

INSERT INTO product_modifier_group (product_id, group_id, sort_order) -- sort_order is the 0-based list index
VALUES ('0190a000-0000-7000-8000-000000000401', '0190a000-0000-7000-8000-000000000302', 0),
       ('0190a000-0000-7000-8000-000000000402', '0190a000-0000-7000-8000-000000000302', 0),
       ('0190a000-0000-7000-8000-000000000411', '0190a000-0000-7000-8000-000000000301', 0),
       ('0190a000-0000-7000-8000-000000000411', '0190a000-0000-7000-8000-000000000302', 1),
       ('0190a000-0000-7000-8000-000000000413', '0190a000-0000-7000-8000-000000000301', 0);
