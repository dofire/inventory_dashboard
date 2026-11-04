-- Synthetic showroom inventory for local QA and demos.
-- Requires public.vehicle from ../table/vehicle.sql to exist first.
-- Inserts 1,000 vehicles: 600 for Ho Chi Minh City and 400 for Hanoi.
-- Prices are representative VND asking prices, not live offers or on-road quotes.
-- IDs are stable (ending 1001 through 2000); reruns leave existing rows untouched.

BEGIN;

WITH catalog (make, model, base_price_vnd, popularity) AS (
    VALUES
        ('Toyota', 'Vios 1.5E MT',                 458000000::bigint, 10),
        ('Toyota', 'Vios 1.5G CVT',                545000000::bigint, 12),
        ('Toyota', 'Wigo G',                       405000000::bigint,  6),
        ('Toyota', 'Yaris Cross G',                650000000::bigint,  7),
        ('Toyota', 'Raize Turbo',                  510000000::bigint,  6),
        ('Toyota', 'Corolla Cross 1.8V',            820000000::bigint, 10),
        ('Toyota', 'Corolla Cross HEV',             865000000::bigint,  4),
        ('Toyota', 'Corolla Altis 1.8V',            780000000::bigint,  3),
        ('Toyota', 'Camry 2.0Q',                   1220000000::bigint,  3),
        ('Toyota', 'Innova Cross 2.0V',             825000000::bigint,  5),
        ('Toyota', 'Veloz Cross CVT',               638000000::bigint,  6),
        ('Toyota', 'Fortuner Legender 4x2',        1295000000::bigint,  3),
        ('Honda', 'City L',                        539000000::bigint,  7),
        ('Honda', 'City RS',                       569000000::bigint,  7),
        ('Honda', 'HR-V G',                        669000000::bigint,  5),
        ('Honda', 'HR-V e:HEV RS',                 869000000::bigint,  2),
        ('Honda', 'CR-V G',                       1039000000::bigint,  4),
        ('Honda', 'CR-V e:HEV RS',                1250000000::bigint,  2),
        ('Honda', 'Civic RS',                      870000000::bigint,  3),
        ('Honda', 'BR-V L',                        705000000::bigint,  3),
        ('Hyundai', 'Accent 1.5 AT',               529000000::bigint,  8),
        ('Hyundai', 'Creta N Line',                715000000::bigint,  7),
        ('Hyundai', 'Tucson 2.0 AT',               889000000::bigint,  5),
        ('Hyundai', 'Santa Fe 2.5 Turbo',         1200000000::bigint,  3),
        ('Hyundai', 'Grand i10 1.2 AT',            360000000::bigint,  6),
        ('Hyundai', 'Stargazer X',                 650000000::bigint,  4),
        ('Hyundai', 'Custin 1.5T',                 850000000::bigint,  2),
        ('Kia', 'Morning GT-Line',                 439000000::bigint,  4),
        ('Kia', 'K3 2.0 Premium',                  699000000::bigint,  4),
        ('Kia', 'Sonet Premium',                   624000000::bigint,  5),
        ('Kia', 'Seltos Luxury',                   749000000::bigint,  6),
        ('Kia', 'Carens Signature',                799000000::bigint,  3),
        ('Kia', 'Sportage 2.0G Signature',         919000000::bigint,  3),
        ('Kia', 'Carnival 2.2D Premium',          1499000000::bigint,  2),
        ('Ford', 'Ranger XLS 2.0L',                707000000::bigint,  8),
        ('Ford', 'Ranger Wildtrak 2.0L',          1039000000::bigint,  6),
        ('Ford', 'Territory Titanium X',           875000000::bigint,  6),
        ('Ford', 'Everest Titanium 4x2',          1299000000::bigint,  5),
        ('Ford', 'Transit Premium 9-seat',          999000000::bigint,  2),
        ('Mitsubishi', 'Xpander AT Premium',        658000000::bigint,  8),
        ('Mitsubishi', 'Xpander Cross',              698000000::bigint,  5),
        ('Mitsubishi', 'Xforce Ultimate',            705000000::bigint,  6),
        ('Mitsubishi', 'Attrage CVT Premium',        490000000::bigint,  3),
        ('Mitsubishi', 'Outlander 2.0 CVT',          825000000::bigint,  2),
        ('Mitsubishi', 'Triton Athlete 4WD',        1039000000::bigint,  3),
        ('Mazda', 'Mazda3 1.5 Luxury',              699000000::bigint,  4),
        ('Mazda', 'CX-3 1.5 Premium',                659000000::bigint,  3),
        ('Mazda', 'CX-30 2.0 Premium',               699000000::bigint,  3),
        ('Mazda', 'CX-5 2.0 Premium Sport',          849000000::bigint,  7),
        ('Mazda', 'CX-8 2.5 Premium',               1129000000::bigint,  2),
        ('VinFast', 'VF 3 Plus',                    299000000::bigint,  9),
        ('VinFast', 'VF 5 Plus',                    529000000::bigint,  8),
        ('VinFast', 'VF 6 Plus',                    759000000::bigint,  6),
        ('VinFast', 'VF 7 Plus',                    999000000::bigint,  4),
        ('VinFast', 'VF 8 Plus',                   1199000000::bigint,  3),
        ('VinFast', 'VF 9 Plus',                   1499000000::bigint,  1),
        ('Suzuki', 'XL7 Hybrid',                    599000000::bigint,  3),
        ('Suzuki', 'Jimny 4AT',                    789000000::bigint,  1),
        ('Nissan', 'Almera VL',                    539000000::bigint,  2),
        ('Nissan', 'Kicks e-POWER',                 789000000::bigint,  2),
        ('Nissan', 'Navara PRO-4X',                 970000000::bigint,  1)
),
weighted_catalog AS (
    SELECT
        catalog.make,
        catalog.model,
        catalog.base_price_vnd,
        row_number() OVER (
            ORDER BY catalog.make, catalog.model, copies.copy_number
        ) AS catalog_slot,
        count(*) OVER () AS catalog_slots
    FROM catalog
    CROSS JOIN LATERAL generate_series(1, catalog.popularity) AS copies(copy_number)
),
numbered AS (
    SELECT
        generated.seq,
        catalog.make,
        catalog.model,
        catalog.base_price_vnd,
        CASE
            WHEN generated.seq <= 600 THEN 'DEALER_HCM_001'
            ELSE 'DEALER_HN_002'
        END AS dealership_id,
        (generated.seq * 31) % 100 AS status_bucket,
        (generated.seq * 73) % 100 AS age_bucket
    FROM generate_series(1, 1000) AS generated(seq)
    JOIN weighted_catalog AS catalog
      ON catalog.catalog_slot = ((generated.seq * 37 - 1) % catalog.catalog_slots) + 1
),
vehicles AS (
    SELECT
        ('00000000-0000-0000-0000-' || lpad((seq + 1000)::text, 12, '0'))::uuid AS id,
        dealership_id,
        make,
        model,
        CASE
            WHEN status_bucket < 76 THEN 'AVAILABLE'
            WHEN status_bucket < 86 THEN 'RESERVED'
            WHEN status_bucket < 94 THEN 'SOLD'
            WHEN status_bucket < 98 THEN 'DRAFT'
            ELSE 'UNAVAILABLE'
        END AS status,
        base_price_vnd + (((seq * 29) % 15) - 7) * 1000000::bigint AS price,
        CASE
            WHEN age_bucket < 50 THEN (seq * 43) % 45
            WHEN age_bucket < 80 THEN 45 + (seq * 43) % 46
            ELSE 91 + (seq * 43) % 275
        END AS age_days
    FROM numbered
)
INSERT INTO public.vehicle (
    id,
    dealership_id,
    make,
    model,
    status,
    price,
    currency,
    stocked_at,
    version
)
SELECT
    id,
    dealership_id,
    make,
    model,
    status,
    price,
    'VND',
    CURRENT_TIMESTAMP - age_days * INTERVAL '1 day',
    0
FROM vehicles
ON CONFLICT (id) DO NOTHING;

COMMIT;
