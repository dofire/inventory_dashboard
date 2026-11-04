-- Adopt existing local tables without replacing their data.
CREATE TABLE IF NOT EXISTS public.vehicle (
    id UUID PRIMARY KEY,
    dealership_id VARCHAR(50) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_vehicle_stocked_id
    ON public.vehicle (stocked_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_vehicle_dealership_stocked_id
    ON public.vehicle (dealership_id, stocked_at DESC, id DESC);

COMMENT ON COLUMN public.vehicle.price IS 'Amount in currency minor units (e.g. cents for USD)';
COMMENT ON COLUMN public.vehicle.currency IS 'Uppercase ISO 4217 currency code (e.g. USD, VND)';
CREATE TABLE IF NOT EXISTS public.vehicle_action (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL,
    action VARCHAR(255) NOT NULL,
    action_status VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID NOT NULL,
    CONSTRAINT fk_vehicle_action_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES public.vehicle (id),
    CONSTRAINT chk_vehicle_action_action CHECK (
        action IN ('PRICE_REDUCTION_PLANNED', 'MAINTENANCE_PLANNED')
    ),
    CONSTRAINT chk_vehicle_action_status CHECK (
        action_status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS idx_vehicle_action_vehicle
    ON public.vehicle_action (vehicle_id);
CREATE INDEX IF NOT EXISTS idx_vehicle_action_latest ON public.vehicle_action (vehicle_id, version DESC, created_at DESC, id DESC);
