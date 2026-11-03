-- PostgreSQL reference DDL for VehicleActionEntity; create vehicle first.
-- UUIDs, audit fields, and version updates are supplied by the application.
CREATE TABLE public.vehicle_action (
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

CREATE INDEX idx_vehicle_action_vehicle
    ON public.vehicle_action (vehicle_id);
