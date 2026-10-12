-- DEV ONLY: a full restaurant menu for the Amadya demo: starters, soups, salads, pizza, pasta, mains, desserts,
-- coffee, beer & wine and cocktails, next to the street-food dishes. Food at the reduced 11% VAT rate (…0002),
-- drinks at 21% (…0001). Stations: kitchen (…0101), grill (…0102), bar (…0103) and a new pizza oven (…0104).

INSERT INTO station (id, code, name, parallel_slots)
VALUES ('0190a000-0000-7000-8000-000000000104', 'PIZZA', '{"ro":"Cuptor pizza","en":"Pizza oven"}', 4);

-- The menu in the order a restaurant reads it: starters first, drinks last.
INSERT INTO category (id, name, sort_order)
VALUES ('0190a000-0000-7000-8000-000000000207', '{"ro":"Aperitive","en":"Starters"}', 10),
       ('0190a000-0000-7000-8000-000000000208', '{"ro":"Ciorbe și supe","en":"Soups"}', 20),
       ('0190a000-0000-7000-8000-000000000209', '{"ro":"Salate","en":"Salads"}', 30),
       ('0190a000-0000-7000-8000-000000000210', '{"ro":"Pizza","en":"Pizza"}', 40),
       ('0190a000-0000-7000-8000-000000000211', '{"ro":"Paste","en":"Pasta"}', 50),
       ('0190a000-0000-7000-8000-000000000212', '{"ro":"Fel principal","en":"Mains"}', 60),
       ('0190a000-0000-7000-8000-000000000213', '{"ro":"Deserturi","en":"Desserts"}', 120),
       ('0190a000-0000-7000-8000-000000000214', '{"ro":"Cafea și ceai","en":"Coffee & tea"}', 130),
       ('0190a000-0000-7000-8000-000000000215', '{"ro":"Bere și vin","en":"Beer & wine"}', 150),
       ('0190a000-0000-7000-8000-000000000216', '{"ro":"Cocktailuri","en":"Cocktails"}', 160);

UPDATE category SET sort_order = 70 WHERE id = '0190a000-0000-7000-8000-000000000201'; -- Burgeri
UPDATE category SET sort_order = 80 WHERE id = '0190a000-0000-7000-8000-000000000202'; -- Lipii și sandvișuri
UPDATE category SET sort_order = 90 WHERE id = '0190a000-0000-7000-8000-000000000203'; -- Platouri
UPDATE category SET sort_order = 100, name = '{"ro":"Mâncare tradițională","en":"Romanian classics"}'
WHERE id = '0190a000-0000-7000-8000-000000000204';
UPDATE category SET sort_order = 110 WHERE id = '0190a000-0000-7000-8000-000000000205'; -- Garnituri
UPDATE category SET sort_order = 140, name = '{"ro":"Răcoritoare","en":"Soft drinks"}'
WHERE id = '0190a000-0000-7000-8000-000000000206';

-- Coffee moves to its own category.
UPDATE product SET category_id = '0190a000-0000-7000-8000-000000000214', sort_order = 1 WHERE id = '0190a000-0000-7000-8000-000000000462'; -- Espresso
UPDATE product SET category_id = '0190a000-0000-7000-8000-000000000214', sort_order = 5 WHERE id = '0190a000-0000-7000-8000-000000000463'; -- Frappé

-- Options
INSERT INTO modifier_group (id, name, min_select, max_select)
VALUES ('0190a000-0000-7000-8000-000000000303', '{"ro":"Mărime","en":"Size"}', 1, 1),
       ('0190a000-0000-7000-8000-000000000304', '{"ro":"Topping extra","en":"Extra toppings"}', 0, 4),
       ('0190a000-0000-7000-8000-000000000305', '{"ro":"Gradul de gătire","en":"Doneness"}', 1, 1),
       ('0190a000-0000-7000-8000-000000000306', '{"ro":"Arome","en":"Flavours"}', 1, 3);

INSERT INTO modifier_option (id, group_id, name, price_delta, sort_order)
VALUES ('0190a000-0000-7000-8000-000000000331', '0190a000-0000-7000-8000-000000000303', '{"ro":"Medie (32 cm)","en":"Medium (32 cm)"}', 0, 1),
       ('0190a000-0000-7000-8000-000000000332', '0190a000-0000-7000-8000-000000000303', '{"ro":"Mare (40 cm)","en":"Large (40 cm)"}', 12, 2),
       ('0190a000-0000-7000-8000-000000000341', '0190a000-0000-7000-8000-000000000304', '{"ro":"Mozzarella extra","en":"Extra mozzarella"}', 5, 1),
       ('0190a000-0000-7000-8000-000000000342', '0190a000-0000-7000-8000-000000000304', '{"ro":"Ciuperci","en":"Mushrooms"}', 4, 2),
       ('0190a000-0000-7000-8000-000000000343', '0190a000-0000-7000-8000-000000000304', '{"ro":"Prosciutto crudo","en":"Prosciutto crudo"}', 8, 3),
       ('0190a000-0000-7000-8000-000000000344', '0190a000-0000-7000-8000-000000000304', '{"ro":"Ardei jalapeño","en":"Jalapeño peppers"}', 3, 4),
       ('0190a000-0000-7000-8000-000000000345', '0190a000-0000-7000-8000-000000000304', '{"ro":"Măsline","en":"Olives"}', 3, 5),
       ('0190a000-0000-7000-8000-000000000351', '0190a000-0000-7000-8000-000000000305', '{"ro":"În sânge","en":"Rare"}', 0, 1),
       ('0190a000-0000-7000-8000-000000000352', '0190a000-0000-7000-8000-000000000305', '{"ro":"Mediu","en":"Medium"}', 0, 2),
       ('0190a000-0000-7000-8000-000000000353', '0190a000-0000-7000-8000-000000000305', '{"ro":"Bine făcut","en":"Well done"}', 0, 3),
       ('0190a000-0000-7000-8000-000000000361', '0190a000-0000-7000-8000-000000000306', '{"ro":"Vanilie","en":"Vanilla"}', 0, 1),
       ('0190a000-0000-7000-8000-000000000362', '0190a000-0000-7000-8000-000000000306', '{"ro":"Ciocolată","en":"Chocolate"}', 0, 2),
       ('0190a000-0000-7000-8000-000000000363', '0190a000-0000-7000-8000-000000000306', '{"ro":"Căpșuni","en":"Strawberry"}', 0, 3),
       ('0190a000-0000-7000-8000-000000000364', '0190a000-0000-7000-8000-000000000306', '{"ro":"Fistic","en":"Pistachio"}', 2, 4);

INSERT INTO product (id, category_id, name, description, price, vat_rate_id, station_id, kind, prep_time_sec, sort_order, allergens)
VALUES
  -- Starters
  ('0190a000-0000-7000-8000-000000000801', '0190a000-0000-7000-8000-000000000207', '{"ro":"Bruschete cu roșii","en":"Tomato bruschetta"}',
   '{"ro":"Pâine prăjită cu roșii, busuioc, usturoi și ulei de măsline","en":"Toasted bread with tomatoes, basil, garlic and olive oil"}',
   24.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 360, 1, '{gluten}'),
  ('0190a000-0000-7000-8000-000000000802', '0190a000-0000-7000-8000-000000000207', '{"ro":"Platou de brânzeturi românești","en":"Romanian cheese board"}',
   '{"ro":"Telemea, cașcaval afumat, brânză de burduf, nuci și miere","en":"Telemea, smoked cheese, burduf cheese, walnuts and honey"}',
   48.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 300, 2, '{lapte,nuci}'),
  ('0190a000-0000-7000-8000-000000000803', '0190a000-0000-7000-8000-000000000207', '{"ro":"Aripioare picante","en":"Spicy chicken wings"}',
   '{"ro":"Aripioare la grătar cu sos BBQ picant și dip de iaurt","en":"Grilled wings with spicy BBQ sauce and a yoghurt dip"}',
   34.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 600, 3, '{lapte}'),
  ('0190a000-0000-7000-8000-000000000804', '0190a000-0000-7000-8000-000000000207', '{"ro":"Cașcaval pane","en":"Breaded cheese"}',
   '{"ro":"Cașcaval pane, cu sos tartar","en":"Fried breaded cheese with tartar sauce"}',
   29.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 420, 4, '{gluten,lapte,ou}'),

  -- Soups
  ('0190a000-0000-7000-8000-000000000811', '0190a000-0000-7000-8000-000000000208', '{"ro":"Ciorbă de burtă","en":"Tripe soup"}',
   '{"ro":"Cu smântână, usturoi și ardei iute","en":"With sour cream, garlic and hot pepper"}',
   26.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 180, 1, '{lapte,ou}'),
  ('0190a000-0000-7000-8000-000000000812', '0190a000-0000-7000-8000-000000000208', '{"ro":"Ciorbă de perișoare","en":"Meatball soup"}',
   '{"ro":"Perișoare de porc și vită, legume, leuștean","en":"Pork and beef meatballs, vegetables, lovage"}',
   24.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 180, 2, '{ou,telina}'),
  ('0190a000-0000-7000-8000-000000000813', '0190a000-0000-7000-8000-000000000208', '{"ro":"Supă cremă de ciuperci","en":"Cream of mushroom soup"}',
   '{"ro":"Cu crutoane și ulei de trufe","en":"With croutons and truffle oil"}',
   22.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 180, 3, '{gluten,lapte}'),

  -- Salads
  ('0190a000-0000-7000-8000-000000000821', '0190a000-0000-7000-8000-000000000209', '{"ro":"Salată Caesar cu pui","en":"Chicken Caesar salad"}',
   '{"ro":"Salată romană, piept de pui la grătar, parmezan, crutoane, dressing Caesar","en":"Romaine, grilled chicken, parmesan, croutons, Caesar dressing"}',
   36.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 420, 1, '{gluten,lapte,ou,peste}'),
  ('0190a000-0000-7000-8000-000000000822', '0190a000-0000-7000-8000-000000000209', '{"ro":"Salată grecească","en":"Greek salad"}',
   '{"ro":"Roșii, castraveți, ardei, ceapă roșie, măsline, feta","en":"Tomatoes, cucumber, peppers, red onion, olives, feta"}',
   32.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 300, 2, '{lapte}'),
  ('0190a000-0000-7000-8000-000000000823', '0190a000-0000-7000-8000-000000000209', '{"ro":"Salată cu ton","en":"Tuna salad"}',
   '{"ro":"Ton, ou, porumb, fasole roșie, salată verde, lămâie","en":"Tuna, egg, sweetcorn, red beans, lettuce, lemon"}',
   34.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 300, 3, '{ou,peste}'),

  -- Pizza
  ('0190a000-0000-7000-8000-000000000831', '0190a000-0000-7000-8000-000000000210', '{"ro":"Pizza Margherita","en":"Margherita pizza"}',
   '{"ro":"Sos de roșii, mozzarella, busuioc proaspăt","en":"Tomato sauce, mozzarella, fresh basil"}',
   34.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000104', 'RECIPE', 720, 1, '{gluten,lapte}'),
  ('0190a000-0000-7000-8000-000000000832', '0190a000-0000-7000-8000-000000000210', '{"ro":"Pizza Quattro Formaggi","en":"Four cheese pizza"}',
   '{"ro":"Mozzarella, gorgonzola, parmezan, cașcaval afumat","en":"Mozzarella, gorgonzola, parmesan, smoked cheese"}',
   42.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000104', 'RECIPE', 720, 2, '{gluten,lapte}'),
  ('0190a000-0000-7000-8000-000000000833', '0190a000-0000-7000-8000-000000000210', '{"ro":"Pizza Diavola","en":"Diavola pizza"}',
   '{"ro":"Sos de roșii, mozzarella, salam picant, ardei iute","en":"Tomato sauce, mozzarella, spicy salami, chilli"}',
   40.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000104', 'RECIPE', 720, 3, '{gluten,lapte}'),
  ('0190a000-0000-7000-8000-000000000834', '0190a000-0000-7000-8000-000000000210', '{"ro":"Pizza Capricciosa","en":"Capricciosa pizza"}',
   '{"ro":"Șuncă, ciuperci, anghinare, măsline, mozzarella","en":"Ham, mushrooms, artichokes, olives, mozzarella"}',
   42.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000104', 'RECIPE', 780, 4, '{gluten,lapte}'),
  ('0190a000-0000-7000-8000-000000000835', '0190a000-0000-7000-8000-000000000210', '{"ro":"Pizza Prosciutto e Funghi","en":"Ham & mushroom pizza"}',
   '{"ro":"Prosciutto cotto, ciuperci, mozzarella, sos de roșii","en":"Cooked ham, mushrooms, mozzarella, tomato sauce"}',
   40.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000104', 'RECIPE', 720, 5, '{gluten,lapte}'),
  ('0190a000-0000-7000-8000-000000000836', '0190a000-0000-7000-8000-000000000210', '{"ro":"Pizza Vegetariana","en":"Vegetarian pizza"}',
   '{"ro":"Legume la grătar, roșii cherry, rucola, mozzarella","en":"Grilled vegetables, cherry tomatoes, rocket, mozzarella"}',
   38.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000104', 'RECIPE', 720, 6, '{gluten,lapte}'),

  -- Pasta
  ('0190a000-0000-7000-8000-000000000841', '0190a000-0000-7000-8000-000000000211', '{"ro":"Spaghete Carbonara","en":"Spaghetti carbonara"}',
   '{"ro":"Guanciale, gălbenuș, pecorino, piper negru","en":"Guanciale, egg yolk, pecorino, black pepper"}',
   38.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 600, 1, '{gluten,lapte,ou}'),
  ('0190a000-0000-7000-8000-000000000842', '0190a000-0000-7000-8000-000000000211', '{"ro":"Tagliatelle Bolognese","en":"Tagliatelle bolognese"}',
   '{"ro":"Ragù de vită gătit încet, parmezan","en":"Slow-cooked beef ragù, parmesan"}',
   36.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 600, 2, '{gluten,lapte,ou,telina}'),
  ('0190a000-0000-7000-8000-000000000843', '0190a000-0000-7000-8000-000000000211', '{"ro":"Penne all''arrabbiata","en":"Penne all''arrabbiata"}',
   '{"ro":"Sos de roșii picant, usturoi, pătrunjel","en":"Spicy tomato sauce, garlic, parsley"}',
   32.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 540, 3, '{gluten}'),
  ('0190a000-0000-7000-8000-000000000844', '0190a000-0000-7000-8000-000000000211', '{"ro":"Linguine cu fructe de mare","en":"Seafood linguine"}',
   '{"ro":"Creveți, midii, calamar, vin alb, roșii cherry","en":"Prawns, mussels, squid, white wine, cherry tomatoes"}',
   52.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 720, 4, '{gluten,crustacee,moluste}'),

  -- Mains
  ('0190a000-0000-7000-8000-000000000851', '0190a000-0000-7000-8000-000000000212', '{"ro":"Antricot de vită 300 g","en":"Rib-eye steak 300 g"}',
   '{"ro":"Maturat, la grătar, cu unt cu ierburi și cartofi la cuptor","en":"Dry-aged, grilled, with herb butter and roast potatoes"}',
   98.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 900, 1, '{lapte}'),
  ('0190a000-0000-7000-8000-000000000852', '0190a000-0000-7000-8000-000000000212', '{"ro":"Somon la grătar","en":"Grilled salmon"}',
   '{"ro":"Cu sparanghel, lămâie și sos de iaurt cu mărar","en":"With asparagus, lemon and a dill yoghurt sauce"}',
   72.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 780, 2, '{peste,lapte}'),
  ('0190a000-0000-7000-8000-000000000853', '0190a000-0000-7000-8000-000000000212', '{"ro":"Ceafă de porc la grătar","en":"Grilled pork neck"}',
   '{"ro":"Cu cartofi țărănești și murături","en":"With country potatoes and pickles"}',
   48.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 840, 3, '{}'),
  ('0190a000-0000-7000-8000-000000000854', '0190a000-0000-7000-8000-000000000212', '{"ro":"Piept de pui cu legume","en":"Chicken breast with vegetables"}',
   '{"ro":"Piept de pui la grătar cu legume de sezon","en":"Grilled chicken breast with seasonal vegetables"}',
   42.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 720, 4, '{}'),

  -- Romanian classics
  ('0190a000-0000-7000-8000-000000000855', '0190a000-0000-7000-8000-000000000204', '{"ro":"Sarmale cu mămăligă","en":"Cabbage rolls with polenta"}',
   '{"ro":"Sarmale în foi de varză murată, cu smântână și ardei iute","en":"Pickled cabbage rolls with sour cream and hot pepper"}',
   38.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 300, 2, '{lapte}'),
  ('0190a000-0000-7000-8000-000000000856', '0190a000-0000-7000-8000-000000000204', '{"ro":"Tochitură moldovenească","en":"Moldavian pork stew"}',
   '{"ro":"Cu mămăligă, brânză de burduf și ou ochi","en":"With polenta, burduf cheese and a fried egg"}',
   44.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 600, 3, '{lapte,ou}'),

  -- Sides
  ('0190a000-0000-7000-8000-000000000861', '0190a000-0000-7000-8000-000000000205', '{"ro":"Piure de cartofi","en":"Mashed potatoes"}', NULL,
   12.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 180, 2, '{lapte}'),
  ('0190a000-0000-7000-8000-000000000862', '0190a000-0000-7000-8000-000000000205', '{"ro":"Legume la grătar","en":"Grilled vegetables"}', NULL,
   16.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000102', 'RECIPE', 420, 3, '{}'),
  ('0190a000-0000-7000-8000-000000000863', '0190a000-0000-7000-8000-000000000205', '{"ro":"Mămăligă","en":"Polenta"}', NULL,
   8.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 120, 4, '{}'),

  -- Desserts
  ('0190a000-0000-7000-8000-000000000871', '0190a000-0000-7000-8000-000000000213', '{"ro":"Papanași","en":"Papanași doughnuts"}',
   '{"ro":"Cu smântână și dulceață de afine","en":"With sour cream and blueberry jam"}',
   28.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 600, 1, '{gluten,lapte,ou}'),
  ('0190a000-0000-7000-8000-000000000872', '0190a000-0000-7000-8000-000000000213', '{"ro":"Tiramisu","en":"Tiramisu"}',
   '{"ro":"Mascarpone, pișcoturi cu espresso, cacao","en":"Mascarpone, espresso-soaked ladyfingers, cocoa"}',
   26.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 120, 2, '{gluten,lapte,ou}'),
  ('0190a000-0000-7000-8000-000000000873', '0190a000-0000-7000-8000-000000000213', '{"ro":"Cheesecake cu fructe de pădure","en":"Berry cheesecake"}', NULL,
   27.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 120, 3, '{gluten,lapte,ou}'),
  ('0190a000-0000-7000-8000-000000000874', '0190a000-0000-7000-8000-000000000213', '{"ro":"Clătite cu ciocolată","en":"Chocolate crêpes"}',
   '{"ro":"Două clătite cu cremă de ciocolată și alune","en":"Two crêpes with chocolate hazelnut spread"}',
   22.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000101', 'RECIPE', 420, 4, '{gluten,lapte,ou,nuci}'),
  ('0190a000-0000-7000-8000-000000000875', '0190a000-0000-7000-8000-000000000213', '{"ro":"Înghețată (3 cupe)","en":"Ice cream (3 scoops)"}',
   '{"ro":"Alegeți până la trei arome","en":"Choose up to three flavours"}',
   18.00, '0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 120, 5, '{lapte}'),

  -- Coffee & tea (the espresso and the frappé move here)
  ('0190a000-0000-7000-8000-000000000881', '0190a000-0000-7000-8000-000000000214', '{"ro":"Cappuccino","en":"Cappuccino"}', NULL,
   12.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 120, 2, '{lapte}'),
  ('0190a000-0000-7000-8000-000000000882', '0190a000-0000-7000-8000-000000000214', '{"ro":"Latte macchiato","en":"Latte macchiato"}', NULL,
   14.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 150, 3, '{lapte}'),
  ('0190a000-0000-7000-8000-000000000883', '0190a000-0000-7000-8000-000000000214', '{"ro":"Ciocolată caldă","en":"Hot chocolate"}', NULL,
   14.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 150, 4, '{lapte}'),
  ('0190a000-0000-7000-8000-000000000884', '0190a000-0000-7000-8000-000000000214', '{"ro":"Ceai","en":"Tea"}',
   '{"ro":"Negru, verde sau de fructe","en":"Black, green or fruit"}',
   10.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 90, 6, '{}'),

  -- Soft drinks (with the existing lemonade, cola and water)
  ('0190a000-0000-7000-8000-000000000885', '0190a000-0000-7000-8000-000000000206', '{"ro":"Fresh de portocale","en":"Fresh orange juice"}', NULL,
   18.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 180, 4, '{}'),
  ('0190a000-0000-7000-8000-000000000886', '0190a000-0000-7000-8000-000000000206', '{"ro":"Fanta 330 ml","en":"Fanta 330 ml"}', NULL,
   8.00, '0190a000-0000-7000-8000-000000000001', NULL, 'RESALE', 0, 5, '{}'),
  ('0190a000-0000-7000-8000-000000000887', '0190a000-0000-7000-8000-000000000206', '{"ro":"Apă minerală 500 ml","en":"Sparkling water 500 ml"}', NULL,
   6.00, '0190a000-0000-7000-8000-000000000001', NULL, 'RESALE', 0, 6, '{}'),

  -- Beer & wine
  ('0190a000-0000-7000-8000-000000000891', '0190a000-0000-7000-8000-000000000215', '{"ro":"Bere la halbă 500 ml","en":"Draught beer 500 ml"}', NULL,
   14.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 60, 1, '{gluten}'),
  ('0190a000-0000-7000-8000-000000000892', '0190a000-0000-7000-8000-000000000215', '{"ro":"Bere artizanală IPA 330 ml","en":"Craft IPA 330 ml"}', NULL,
   19.00, '0190a000-0000-7000-8000-000000000001', NULL, 'RESALE', 0, 2, '{gluten}'),
  ('0190a000-0000-7000-8000-000000000893', '0190a000-0000-7000-8000-000000000215', '{"ro":"Vin roșu la pahar 150 ml","en":"Red wine by the glass 150 ml"}',
   '{"ro":"Fetească Neagră, Dealu Mare","en":"Fetească Neagră, Dealu Mare"}',
   18.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 60, 3, '{sulfiti}'),
  ('0190a000-0000-7000-8000-000000000894', '0190a000-0000-7000-8000-000000000215', '{"ro":"Vin alb la pahar 150 ml","en":"White wine by the glass 150 ml"}',
   '{"ro":"Fetească Albă, Cotnari","en":"Fetească Albă, Cotnari"}',
   18.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 60, 4, '{sulfiti}'),
  ('0190a000-0000-7000-8000-000000000895', '0190a000-0000-7000-8000-000000000215', '{"ro":"Sticlă de vin roșu 750 ml","en":"Bottle of red wine 750 ml"}',
   '{"ro":"Fetească Neagră, Dealu Mare","en":"Fetească Neagră, Dealu Mare"}',
   85.00, '0190a000-0000-7000-8000-000000000001', NULL, 'RESALE', 0, 5, '{sulfiti}'),
  ('0190a000-0000-7000-8000-000000000896', '0190a000-0000-7000-8000-000000000215', '{"ro":"Prosecco la pahar","en":"Prosecco by the glass"}', NULL,
   22.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 60, 6, '{sulfiti}'),

  -- Cocktails
  ('0190a000-0000-7000-8000-000000000897', '0190a000-0000-7000-8000-000000000216', '{"ro":"Aperol Spritz","en":"Aperol Spritz"}',
   '{"ro":"Aperol, prosecco, apă minerală, portocală","en":"Aperol, prosecco, soda, orange"}',
   32.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 180, 1, '{sulfiti}'),
  ('0190a000-0000-7000-8000-000000000898', '0190a000-0000-7000-8000-000000000216', '{"ro":"Mojito","en":"Mojito"}',
   '{"ro":"Rom alb, lime, mentă, zahăr brun, apă minerală","en":"White rum, lime, mint, brown sugar, soda"}',
   30.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 240, 2, '{}'),
  ('0190a000-0000-7000-8000-000000000899', '0190a000-0000-7000-8000-000000000216', '{"ro":"Hugo","en":"Hugo"}',
   '{"ro":"Prosecco, sirop de soc, mentă, lime","en":"Prosecco, elderflower syrup, mint, lime"}',
   30.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 180, 3, '{sulfiti}'),
  ('0190a000-0000-7000-8000-000000000900', '0190a000-0000-7000-8000-000000000216', '{"ro":"Mocktail cu fructe de pădure","en":"Berry mocktail"}',
   '{"ro":"Fără alcool: fructe de pădure, lime, apă minerală","en":"Alcohol-free: berries, lime, soda"}',
   22.00, '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000103', 'RECIPE', 180, 4, '{}');

INSERT INTO product_modifier_group (product_id, group_id, sort_order) -- sort_order is the 0-based list index
VALUES ('0190a000-0000-7000-8000-000000000831', '0190a000-0000-7000-8000-000000000303', 0),
       ('0190a000-0000-7000-8000-000000000831', '0190a000-0000-7000-8000-000000000304', 1),
       ('0190a000-0000-7000-8000-000000000832', '0190a000-0000-7000-8000-000000000303', 0),
       ('0190a000-0000-7000-8000-000000000832', '0190a000-0000-7000-8000-000000000304', 1),
       ('0190a000-0000-7000-8000-000000000833', '0190a000-0000-7000-8000-000000000303', 0),
       ('0190a000-0000-7000-8000-000000000833', '0190a000-0000-7000-8000-000000000304', 1),
       ('0190a000-0000-7000-8000-000000000834', '0190a000-0000-7000-8000-000000000303', 0),
       ('0190a000-0000-7000-8000-000000000834', '0190a000-0000-7000-8000-000000000304', 1),
       ('0190a000-0000-7000-8000-000000000835', '0190a000-0000-7000-8000-000000000303', 0),
       ('0190a000-0000-7000-8000-000000000835', '0190a000-0000-7000-8000-000000000304', 1),
       ('0190a000-0000-7000-8000-000000000836', '0190a000-0000-7000-8000-000000000303', 0),
       ('0190a000-0000-7000-8000-000000000836', '0190a000-0000-7000-8000-000000000304', 1),
       ('0190a000-0000-7000-8000-000000000851', '0190a000-0000-7000-8000-000000000305', 0),
       ('0190a000-0000-7000-8000-000000000875', '0190a000-0000-7000-8000-000000000306', 0);

INSERT INTO promotion (id, title, subtitle, badge, product_id, sort_order)
VALUES ('0190a000-0000-7000-8000-000000000504',
        '{"ro":"Seara de pizza","en":"Pizza night"}',
        '{"ro":"Margherita din cuptorul nostru, cu busuioc proaspăt","en":"Margherita from our oven, with fresh basil"}',
        '34 LEI', '0190a000-0000-7000-8000-000000000831', 4),
       ('0190a000-0000-7000-8000-000000000505',
        '{"ro":"Un final dulce","en":"A sweet ending"}',
        '{"ro":"Papanași cu smântână și dulceață de afine","en":"Papanași with sour cream and blueberry jam"}',
        '28 LEI', '0190a000-0000-7000-8000-000000000871', 5);
