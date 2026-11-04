package com.dofire.sv_vehicle.api.set_vehicle_action;

import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import com.dofire.sv_vehicle.global.model.VehicleActionType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class SetVehicleActionRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public SetVehicleActionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void logAction(
        String dealershipId,
        UUID vehicleId,
        long rowVersion,
        VehicleActionType action,
        VehicleActionStatus actionStatus,
        UUID userId,
        Instant now
    ) {
        var parameters = new MapSqlParameterSource()
                .addValue("dealershipId", dealershipId)
                .addValue("vehicleId", vehicleId)
                .addValue("rowVersion", rowVersion)
                .addValue("actionId", UUID.randomUUID())
                .addValue("action", action.name())
                .addValue("actionStatus", actionStatus.name())
                .addValue("userId", userId)
                .addValue("now", Timestamp.from(now));
        int updated = jdbc.update(
            """
            UPDATE public.vehicle SET version = version + 1
            WHERE id = :vehicleId AND dealership_id = :dealershipId AND version = :rowVersion
            """,
            parameters
        );
        if (updated == 0) {
            Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM public.vehicle WHERE id = :vehicleId AND dealership_id = :dealershipId)",
                parameters,
                Boolean.class
            );
            throw new ResponseStatusException(
                Boolean.TRUE.equals(exists) ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND,
                Boolean.TRUE.equals(exists) ? "Vehicle has changed; reload and retry" : "Vehicle not found"
            );
        }
        jdbc.update(
            """
            INSERT INTO public.vehicle_action
                (id, vehicle_id, action, action_status, version, created_at, created_by, updated_at, updated_by)
            VALUES
                (:actionId, :vehicleId, :action, :actionStatus, :rowVersion + 1, :now, :userId, :now, :userId)
            """,
            parameters
        );
    }
}
