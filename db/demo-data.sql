START TRANSACTION;

INSERT INTO localizations (city, address, postal_code, state)
SELECT 'Demo City', 'ul. Przykladowa 10', '00-001', 'Mazowieckie'
WHERE NOT EXISTS (
    SELECT 1 FROM localizations
    WHERE city = 'Demo City' AND address = 'ul. Przykladowa 10'
);

SET @localization_id = (
    SELECT id FROM localizations
    WHERE city = 'Demo City' AND address = 'ul. Przykladowa 10'
    ORDER BY id LIMIT 1
);

INSERT INTO buildings (name, description, localization_id)
SELECT 'Centrum Konferencyjne', 'Przykladowy budynek demonstracyjny', @localization_id
WHERE NOT EXISTS (
    SELECT 1 FROM buildings
    WHERE name = 'Centrum Konferencyjne' AND localization_id = @localization_id
);

SET @building_id = (
    SELECT id FROM buildings
    WHERE name = 'Centrum Konferencyjne' AND localization_id = @localization_id
    ORDER BY id LIMIT 1
);

INSERT INTO halls (name, type, capacity, available, description, email, building_id)
SELECT 'Sala Alfa', 'CONFERENCE', '8', 1, 'Sala dla malego zespolu', NULL, @building_id
WHERE NOT EXISTS (
    SELECT 1 FROM halls WHERE name = 'Sala Alfa' AND building_id = @building_id
);

INSERT INTO halls (name, type, capacity, available, description, email, building_id)
SELECT 'Sala Beta', 'CONFERENCE', '16', 1, 'Sala konferencyjna z projektorem', NULL, @building_id
WHERE NOT EXISTS (
    SELECT 1 FROM halls WHERE name = 'Sala Beta' AND building_id = @building_id
);

INSERT INTO halls (name, type, capacity, available, description, email, building_id)
SELECT 'Sala Gamma', 'MEETING', '4', 1, 'Kameralna sala spotkan', NULL, @building_id
WHERE NOT EXISTS (
    SELECT 1 FROM halls WHERE name = 'Sala Gamma' AND building_id = @building_id
);

COMMIT;