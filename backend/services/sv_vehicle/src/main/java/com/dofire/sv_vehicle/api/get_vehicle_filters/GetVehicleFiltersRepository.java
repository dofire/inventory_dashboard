package com.dofire.sv_vehicle.api.get_vehicle_filters;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GetVehicleFiltersRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public GetVehicleFiltersRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, List<String>> findByDealership(String dealershipId) {
        var options = new TreeMap<String, List<String>>();
        jdbc.query(
            """
            SELECT DISTINCT make, model FROM public.vehicle
            WHERE dealership_id = :dealershipId ORDER BY make, model
            """,
            Map.of("dealershipId", dealershipId),
            (org.springframework.jdbc.core.RowCallbackHandler) row -> options
                    .computeIfAbsent(row.getString("make"), key -> new java.util.ArrayList<>())
                    .add(row.getString("model"))
        );
        return options;
    }
}
