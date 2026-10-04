-- DEV ONLY: promotions inspired by the reference posters in burregescu-img/.
INSERT INTO promotion (id, title, subtitle, badge, product_id, sort_order)
VALUES ('0190a000-0000-7000-8000-000000000501',
        '{"ro":"Mai mult gust!","en":"More flavour!"}',
        '{"ro":"Lipie cu pui — alege varianta cu sos cheese sau usturoi","en":"Chicken pita — with cheese or garlic sauce"}',
        '20 LEI', '0190a000-0000-7000-8000-000000000411', 1),
       ('0190a000-0000-7000-8000-000000000502',
        '{"ro":"Când un burger nu e suficient…","en":"When one burger is not enough…"}',
        '{"ro":"Platou întreg: 4 burgeri, cartofi și sosuri","en":"A whole platter: 4 burgers, fries and sauces"}',
        '140 LEI', '0190a000-0000-7000-8000-000000000421', 2),
       ('0190a000-0000-7000-8000-000000000503',
        '{"ro":"Fasole cu afumătură","en":"Beans with smoked pork"}',
        '{"ro":"Un prânz delicios și sățios","en":"A hearty Romanian lunch"}',
        NULL, '0190a000-0000-7000-8000-000000000431', 3);
