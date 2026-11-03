-- PostgreSQL reference DDL for VehicleEntity; not an application migration.
CREATE TABLE public.vehicle (
    id UUID PRIMARY KEY,
    make VARCHAR(255) NOT NULL,
    model VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    price BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    stocked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_vehicle_status CHECK (
        status IN ('DRAFT', 'AVAILABLE', 'RESERVED', 'SOLD', 'UNAVAILABLE')
    )
);

CREATE INDEX idx_vehicle_stocked_id
    ON public.vehicle (stocked_at DESC, id DESC);

CREATE INDEX idx_vehicle_make_stocked_id
    ON public.vehicle (make, stocked_at DESC, id DESC);

COMMENT ON COLUMN public.vehicle.price IS 'Amount in currency minor units (e.g. cents for USD)';
COMMENT ON COLUMN public.vehicle.currency IS 'Uppercase ISO 4217 currency code (e.g. USD, VND)';
