package com.dofire.sv_vehicle.api.get_vehicles;

import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import com.dofire.sv_vehicle.global.model.VehicleActionType;
import com.dofire.sv_vehicle.global.model.VehicleStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GetVehiclesRepository {
    private static final String SELECT_VEHICLES = """
            SELECT v.*, latest.action, latest.action_status
            FROM public.vehicle v
            LEFT JOIN LATERAL (
                SELECT a.action, a.action_status
                FROM public.vehicle_action a
                WHERE a.vehicle_id = v.id
                ORDER BY a.version DESC, a.created_at DESC, a.id DESC
                LIMIT 1
            ) latest ON true
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public GetVehiclesRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<VehicleView> findVehicles(
        String dealershipId,
        int page,
        int size,
        List<String> makes,
        List<String> models,
        Integer age,
        Boolean aging,
        String sortKey,
        String sortOrder,
        Instant now
    ) {
        var filter = vehicleFilter(
            dealershipId,
            makes,
            models,
            age,
            aging,
            now
        );
        var parameters = filter.parameters()
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        String sql = SELECT_VEHICLES + filter.whereClause()
                + " ORDER BY " + orderBy(sortKey, sortOrder)
                + " LIMIT :limit OFFSET :offset";
        return jdbc.query(
            sql,
            parameters,
            this::mapVehicle
        );
    }

    public long countVehicles(
        String dealershipId,
        List<String> makes,
        List<String> models,
        Integer age,
        Boolean aging,
        Instant now
    ) {
        var filter = vehicleFilter(
            dealershipId,
            makes,
            models,
            age,
            aging,
            now
        );
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM public.vehicle v " + filter.whereClause(),
            filter.parameters(),
            Long.class
        );
    }

    private record VehicleFilter(String whereClause, MapSqlParameterSource parameters) {}

    private VehicleFilter vehicleFilter(
        String dealershipId,
        List<String> makes,
        List<String> models,
        Integer age,
        Boolean aging,
        Instant now
    ) {
        var parameters = new MapSqlParameterSource().addValue("dealershipId", dealershipId);
        var sql = new StringBuilder(" WHERE v.dealership_id = :dealershipId");
        addValuesFilter(
            sql,
            parameters,
            "make",
            makes
        );
        addValuesFilter(
            sql,
            parameters,
            "model",
            models
        );
        if (age != null) {
            // Epoch comparison also accepts the API's full nonnegative integer age range.
            sql.append(" AND EXTRACT(EPOCH FROM v.stocked_at) <= :minimumStockedEpoch");
            Instant minimumStockedAt = now.minus(
                age,
                ChronoUnit.DAYS
            );
            parameters.addValue(
                "minimumStockedEpoch",
                BigDecimal.valueOf(minimumStockedAt.getEpochSecond()).add(
                    BigDecimal.valueOf(minimumStockedAt.getNano(), 9)
                )
            );
        }
        if (aging != null) {
            sql.append(aging ? " AND v.stocked_at < :agingBefore" : " AND v.stocked_at >= :agingBefore");
            parameters.addValue("agingBefore", Timestamp.from(now.minus(90, ChronoUnit.DAYS)));
        }
        return new VehicleFilter(
            sql.toString(),
            parameters
        );
    }

    private void addValuesFilter(
        StringBuilder sql,
        MapSqlParameterSource parameters,
        String column,
        List<String> values
    ) {
        if (values != null && !values.isEmpty()) {
            // Column names are internal constants; all request values remain bound parameters.
            sql.append(" AND LOWER(v.").append(column).append(") IN (:").append(column).append(")");
            parameters.addValue(column, values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList());
        }
    }

    private String orderBy(
        String sortKey,
        String sortOrder
    ) {
        String direction = "asc".equalsIgnoreCase(sortOrder) ? " ASC" : " DESC";
        String primary = switch (sortKey) {
            case "make" -> "LOWER(v.make)" + direction + ", LOWER(v.model)";
            case "model" -> "LOWER(v.model)";
            case "price" -> "v.currency" + direction + ", v.price";
            case "status" -> "v.status";
            case "action" -> "COALESCE(latest.action, '')";
            case "daysInStock" -> "v.stocked_at";
            default -> "v.stocked_at";
        };
        if ("daysInStock".equals(sortKey)) {
            return primary + ("asc".equalsIgnoreCase(sortOrder) ? " DESC" : " ASC") + ", v.id" + direction;
        }
        return primary + direction + ", v.id" + direction;
    }

    private VehicleView mapVehicle(
        ResultSet row,
        int rowNumber
    ) throws SQLException {
        String action = row.getString("action");
        String actionStatus = row.getString("action_status");
        return new VehicleView(
            row.getObject("id", UUID.class),
            row.getString("dealership_id"),
            row.getString("make"),
            row.getString("model"),
            VehicleStatus.valueOf(row.getString("status")),
            row.getLong("price"),
            row.getString("currency"),
            row.getTimestamp("stocked_at").toInstant(),
            row.getLong("version"),
            action == null ? null : VehicleActionType.valueOf(action),
            actionStatus == null ? null : VehicleActionStatus.valueOf(actionStatus)
        );
    }
}
