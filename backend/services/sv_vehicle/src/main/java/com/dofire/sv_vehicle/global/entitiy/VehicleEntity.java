package com.dofire.sv_vehicle.global.entitiy;

import com.dofire.sv_vehicle.vehicle.VehicleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle", schema = "public", indexes = {
        @Index(name = "idx_vehicle_stocked_id", columnList = "stocked_at DESC, id DESC"),
        @Index(name = "idx_vehicle_make_stocked_id", columnList = "make, stocked_at DESC, id DESC")
})
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "stocked_at", nullable = false)
    private Instant stockedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public VehicleEntity(UUID id, String make, String model, VehicleStatus status,
                         BigDecimal price, Instant stockedAt) {
        this.id = id;
        this.make = make;
        this.model = model;
        this.status = status;
        this.price = price;
        this.stockedAt = stockedAt;
    }
}
