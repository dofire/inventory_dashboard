package com.dofire.sv_vehicle.global.entitiy;

import com.dofire.sv_vehicle.vehicle.VehicleActionStatus;
import com.dofire.sv_vehicle.vehicle.VehicleActionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle_action", schema = "public", indexes = {
        @Index(name = "idx_vehicle_action_vehicle", columnList = "vehicle_id")
})
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleActionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleActionType action;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_status", nullable = false)
    private VehicleActionStatus actionStatus;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    public VehicleActionEntity(UUID vehicleId, VehicleActionType action,
                               VehicleActionStatus actionStatus, UUID userId, Instant now) {
        this.id = UUID.randomUUID();
        this.vehicleId = vehicleId;
        this.action = action;
        this.actionStatus = actionStatus;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = userId;
        this.updatedBy = userId;
    }
}
