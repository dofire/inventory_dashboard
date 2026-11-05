package com.dofire.sv_vehicle.api.get_vehicle_history;

import com.dofire.sv_vehicle.global.model.VehicleActionType;
import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class GetVehicleHistoryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public GetVehicleHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count(
        UUID vehicleId,
        String dealershipId
    ) {
        var parameters = Map.of("vehicleId", vehicleId, "dealershipId", dealershipId);
        Boolean exists = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM public.vehicle WHERE id=:vehicleId AND dealership_id=:dealershipId)",
            parameters,
            Boolean.class
        );
        if (!Boolean.TRUE.equals(exists)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found");
        }
        return jdbc.queryForObject(
            "SELECT count(*) FROM public.vehicle_action WHERE vehicle_id=:vehicleId",
            parameters,
            Long.class
        );
    }

    public List<ActionHistoryView> findHistory(
        UUID vehicleId,
        String dealershipId,
        int page,
        int size
    ) {
        return jdbc.query(
            """
            SELECT a.* FROM public.vehicle_action a
            JOIN public.vehicle v ON v.id=a.vehicle_id
            WHERE a.vehicle_id=:vehicleId AND v.dealership_id=:dealershipId
            ORDER BY a.version DESC, a.created_at DESC, a.id DESC
            LIMIT :limit OFFSET :offset
            """,
            Map.of("vehicleId", vehicleId, "dealershipId", dealershipId, "limit", size, "offset", (long) page * size),
            (row, index) -> new ActionHistoryView(
                row.getObject("id", UUID.class),
                row.getBoolean("removed") ? null : VehicleActionType.valueOf(row.getString("action")),
                row.getBoolean("removed") ? null : VehicleActionStatus.valueOf(row.getString("action_status")),
                row.getBoolean("removed"),
                row.getLong("version"),
                row.getTimestamp("created_at").toInstant(),
                row.getObject("created_by", UUID.class)
            )
        );
    }
}
