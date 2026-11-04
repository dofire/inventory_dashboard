-- Synthetic showroom inventory for local QA and demos.
-- Requires public.vehicle from ../table/vehicle.sql to exist first.
-- Inserts 1,000 vehicles: 600 for Ho Chi Minh City and 400 for Hanoi.
-- Prices are illustrative USD cents converted from the VND catalog at a fixed 25,000 VND/USD; they are not live offers.
-- IDs are stable (ending 1001 through 2000). Reruns sync price/currency only
-- when changed and bump version; they preserve status and stocking date.

BEGIN;

WITH catalog (make, model, base_price_usd_cents, popularity) AS (
    VALUES
        ('Toyota', 'Vios 1.5E MT',                 1832000::bigint, 10),
        ('Toyota', 'Vios 1.5G CVT',                2180000::bigint, 12),
        ('Toyota', 'Wigo G',                       1620000::bigint,  6),
        ('Toyota', 'Yaris Cross G',                2600000::bigint,  7),
        ('Toyota', 'Raize Turbo',                  2040000::bigint,  6),
        ('Toyota', 'Corolla Cross 1.8V',            3280000::bigint, 10),
        ('Toyota', 'Corolla Cross HEV',             3460000::bigint,  4),
        ('Toyota', 'Corolla Altis 1.8V',            3120000::bigint,  3),
        ('Toyota', 'Camry 2.0Q',                   4880000::bigint,  3),
        ('Toyota', 'Innova Cross 2.0V',             3300000::bigint,  5),
        ('Toyota', 'Veloz Cross CVT',               2552000::bigint,  6),
        ('Toyota', 'Fortuner Legender 4x2',        5180000::bigint,  3),
        ('Honda', 'City L',                        2156000::bigint,  7),
        ('Honda', 'City RS',                       2276000::bigint,  7),
        ('Honda', 'HR-V G',                        2676000::bigint,  5),
        ('Honda', 'HR-V e:HEV RS',                 3476000::bigint,  2),
        ('Honda', 'CR-V G',                       4156000::bigint,  4),
        ('Honda', 'CR-V e:HEV RS',                5000000::bigint,  2),
        ('Honda', 'Civic RS',                      3480000::bigint,  3),
        ('Honda', 'BR-V L',                        2820000::bigint,  3),
        ('Hyundai', 'Accent 1.5 AT',               2116000::bigint,  8),
        ('Hyundai', 'Creta N Line',                2860000::bigint,  7),
        ('Hyundai', 'Tucson 2.0 AT',               3556000::bigint,  5),
        ('Hyundai', 'Santa Fe 2.5 Turbo',         4800000::bigint,  3),
        ('Hyundai', 'Grand i10 1.2 AT',            1440000::bigint,  6),
        ('Hyundai', 'Stargazer X',                 2600000::bigint,  4),
        ('Hyundai', 'Custin 1.5T',                 3400000::bigint,  2),
        ('Kia', 'Morning GT-Line',                 1756000::bigint,  4),
        ('Kia', 'K3 2.0 Premium',                  2796000::bigint,  4),
        ('Kia', 'Sonet Premium',                   2496000::bigint,  5),
        ('Kia', 'Seltos Luxury',                   2996000::bigint,  6),
        ('Kia', 'Carens Signature',                3196000::bigint,  3),
        ('Kia', 'Sportage 2.0G Signature',         3676000::bigint,  3),
        ('Kia', 'Carnival 2.2D Premium',          5996000::bigint,  2),
        ('Ford', 'Ranger XLS 2.0L',                2828000::bigint,  8),
        ('Ford', 'Ranger Wildtrak 2.0L',          4156000::bigint,  6),
        ('Ford', 'Territory Titanium X',           3500000::bigint,  6),
        ('Ford', 'Everest Titanium 4x2',          5196000::bigint,  5),
        ('Ford', 'Transit Premium 9-seat',          3996000::bigint,  2),
        ('Mitsubishi', 'Xpander AT Premium',        2632000::bigint,  8),
        ('Mitsubishi', 'Xpander Cross',              2792000::bigint,  5),
        ('Mitsubishi', 'Xforce Ultimate',            2820000::bigint,  6),
        ('Mitsubishi', 'Attrage CVT Premium',        1960000::bigint,  3),
        ('Mitsubishi', 'Outlander 2.0 CVT',          3300000::bigint,  2),
        ('Mitsubishi', 'Triton Athlete 4WD',        4156000::bigint,  3),
        ('Mazda', 'Mazda3 1.5 Luxury',              2796000::bigint,  4),
        ('Mazda', 'CX-3 1.5 Premium',                2636000::bigint,  3),
        ('Mazda', 'CX-30 2.0 Premium',               2796000::bigint,  3),
        ('Mazda', 'CX-5 2.0 Premium Sport',          3396000::bigint,  7),
        ('Mazda', 'CX-8 2.5 Premium',               4516000::bigint,  2),
        ('VinFast', 'VF 3 Plus',                    1196000::bigint,  9),
        ('VinFast', 'VF 5 Plus',                    2116000::bigint,  8),
        ('VinFast', 'VF 6 Plus',                    3036000::bigint,  6),
        ('VinFast', 'VF 7 Plus',                    3996000::bigint,  4),
        ('VinFast', 'VF 8 Plus',                   4796000::bigint,  3),
        ('VinFast', 'VF 9 Plus',                   5996000::bigint,  1),
        ('Suzuki', 'XL7 Hybrid',                    2396000::bigint,  3),
        ('Suzuki', 'Jimny 4AT',                    3156000::bigint,  1),
        ('Nissan', 'Almera VL',                    2156000::bigint,  2),
        ('Nissan', 'Kicks e-POWER',                 3156000::bigint,  2),
        ('Nissan', 'Navara PRO-4X',                 3880000::bigint,  1)
),
weighted_catalog AS (
    SELECT
        catalog.make,
        catalog.model,
        catalog.base_price_usd_cents,
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
        catalog.base_price_usd_cents,
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
        base_price_usd_cents + (((seq * 29) % 15) - 7) * 4000::bigint AS price,
        CASE
            WHEN age_bucket < 50 THEN (seq * 43) % 45
            WHEN age_bucket < 80 THEN 45 + (seq * 43) % 46
            ELSE 91 + (seq * 43) % 275
        END AS age_days
    FROM numbered
)
INSERT INTO public.vehicle AS existing (
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
    'USD',
    CURRENT_TIMESTAMP - age_days * INTERVAL '1 day',
    0
FROM vehicles
ON CONFLICT (id) DO UPDATE
SET price = EXCLUDED.price,
    currency = EXCLUDED.currency,
    version = existing.version + 1
WHERE (existing.price, existing.currency)
    IS DISTINCT FROM (EXCLUDED.price, EXCLUDED.currency);

COMMIT;
