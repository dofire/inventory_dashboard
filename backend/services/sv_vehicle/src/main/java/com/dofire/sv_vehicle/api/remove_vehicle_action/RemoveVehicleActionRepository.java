package com.dofire.sv_vehicle.api.remove_vehicle_action;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class RemoveVehicleActionRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public RemoveVehicleActionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void remove(RemoveVehicleActionRequest request) {
        var parameters = new MapSqlParameterSource()
                .addValue("vehicleId", request.getVehicleId())
                .addValue("dealershipId", request.getDealershipId())
                .addValue("rowVersion", request.getRowVersion())
                .addValue("userId", request.getUserId())
                .addValue("id", UUID.randomUUID())
                .addValue("now", Timestamp.from(Instant.now()));
        int updated = jdbc.update(
            "UPDATE public.vehicle SET version=version+1 WHERE id=:vehicleId AND dealership_id=:dealershipId AND version=:rowVersion",
            parameters
        );
        if (updated == 0) {
            Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM public.vehicle WHERE id=:vehicleId AND dealership_id=:dealershipId)",
                parameters,
                Boolean.class
            );
            throw new ResponseStatusException(Boolean.TRUE.equals(exists) ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND);
        }
        int inserted = jdbc.update(
            """
            INSERT INTO public.vehicle_action
                (id, vehicle_id, action, action_status, version, created_at, created_by, updated_at, updated_by, removed)
            SELECT :id, :vehicleId, latest.action, latest.action_status, :rowVersion+1, :now, :userId, :now, :userId, true
            FROM (SELECT action, action_status, removed FROM public.vehicle_action
                  WHERE vehicle_id=:vehicleId ORDER BY version DESC, created_at DESC, id DESC LIMIT 1) latest
            WHERE NOT latest.removed
            """,
            parameters
        );
        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No current proposal");
        }
    }
}
