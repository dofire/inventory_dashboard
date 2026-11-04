package com.dofire.sv_vehicle.api;

import com.dofire.sv_vehicle.api.get_vehicle_filters.GetVehicleFiltersRepository;
import com.dofire.sv_vehicle.api.get_vehicles.GetVehiclesRepository;
import com.dofire.sv_vehicle.api.get_vehicles.VehicleView;
import com.dofire.sv_vehicle.api.set_vehicle_action.SetVehicleActionRepository;
import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import com.dofire.sv_vehicle.global.model.VehicleActionType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/** Uses existing local PostgreSQL; isolated fixture dealerships are rolled back or cleaned up. */
@EnabledIfEnvironmentVariable(named = "POSTGRES_INTEGRATION_TEST", matches = "true")
class PostgresRepositoryTests {
    private NamedParameterJdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private GetVehiclesRepository vehicles;
    private GetVehicleFiltersRepository filters;
    private SetVehicleActionRepository actions;
    private final String dealer = "TEST_" + UUID.randomUUID();
    private final String otherDealer = "TEST_" + UUID.randomUUID();
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @BeforeEach
    void connect() {
        String url = "jdbc:postgresql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "5432")
                + "/" + env("POSTGRES_DB", "vehicle");
        var source = new DriverManagerDataSource(url, env("POSTGRES_USER", "vehicle"), System.getenv("POSTGRES_PASSWORD"));
        jdbc = new NamedParameterJdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        vehicles = new GetVehiclesRepository(jdbc);
        filters = new GetVehicleFiltersRepository(jdbc);
        actions = new SetVehicleActionRepository(jdbc);
    }

    @Test
    void migrationAdoptsExistingSchemaWithoutChangingVehicleRows() {
        Long before = jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM public.vehicle", Long.class);
        var flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(jdbc.getJdbcTemplate().getDataSource())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        flyway.validate();
        assertEquals(before, jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM public.vehicle", Long.class));
    }

    @Test
    void sqlScopesFiltersSortsAndPaginatesBeforeReturningRows() {
        transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            insert(dealer, "Toyota", "Corolla", 100, 120);
            insert(dealer, "Honda", "Civic", 300, 30);
            insert(dealer, "Toyota", "Camry", 200, 95);
            insert(otherDealer, "Mazda", "CX-5", 50, 180);
            assertEquals(List.of(100L, 200L), search(dealer, 0, 2, null, null, null, null, "price", "asc")
                    .stream().map(VehicleView::price).toList());
            assertEquals(3, vehicles.countVehicles(dealer, null, null, null, null, now));
            assertEquals(2, vehicles.countVehicles(dealer, List.of("Toyota"), null, 95, true, now));
            assertEquals(0, vehicles.countVehicles(otherDealer, List.of("Toyota"), null, null, null, now));
            assertTrue(search(dealer, 100, 20, null, null, null, null, "price", "asc").isEmpty());
            assertEquals(3, vehicles.countVehicles(dealer, null, null, null, null, now));
            assertEquals(300, search(dealer, 1, 2, null, null, null, null, "price", "asc").getFirst().price());
            assertEquals(2, search(dealer, 0, 20, List.of("TOYOTA"), null, 95, true, "stockedAt", "desc").size());
            assertEquals("Corolla", search(dealer, 0, 20, List.of("toyota"), List.of("COROLLA"), null, null, "model", "asc").getFirst().model());
            assertEquals(1, search(dealer, 0, 20, null, null, null, false, "daysInStock", "asc").size());
            assertEquals("Civic", search(dealer, 0, 20, null, null, null, null, "daysInStock", "asc").getFirst().model());
            assertEquals(Map.of("Honda", List.of("Civic"), "Toyota", List.of("Camry", "Corolla")), filters.findByDealership(dealer));
            assertEquals(Map.of("Mazda", List.of("CX-5")), filters.findByDealership(otherDealer));
            assertTrue(search(otherDealer, 0, 20, List.of("Toyota"), null, null, null, "price", "asc").isEmpty());
        });
    }

    @Test
    void writesPersistLatestActionAndRejectStaleOrCrossDealershipChanges() {
        transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            UUID id = insert(dealer, "Toyota", "Corolla", 100, 120);
            UUID user = UUID.randomUUID();
            var wrongDealer = assertThrows(ResponseStatusException.class, () -> actions.logAction(
                otherDealer, id, 0, VehicleActionType.MAINTENANCE_PLANNED, VehicleActionStatus.CREATED, user, now
            ));
            assertEquals(HttpStatus.NOT_FOUND, wrongDealer.getStatusCode());
            actions.logAction(dealer, id, 0, VehicleActionType.MAINTENANCE_PLANNED, VehicleActionStatus.CREATED, user, now);
            actions.logAction(dealer, id, 1, VehicleActionType.PRICE_REDUCTION_PLANNED, VehicleActionStatus.COMPLETED, user, now);
            var result = search(dealer, 0, 20, null, null, null, null, "action", "asc").getFirst();
            assertEquals(2, result.rowVersion());
            assertEquals(VehicleActionType.PRICE_REDUCTION_PLANNED, result.action());
            assertEquals(VehicleActionStatus.COMPLETED, result.actionStatus());
            var stale = assertThrows(ResponseStatusException.class, () -> actions.logAction(
                dealer, id, 0, VehicleActionType.MAINTENANCE_PLANNED, VehicleActionStatus.CREATED, user, now
            ));
            assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM public.vehicle_action WHERE vehicle_id=:id", Map.of("id", id), Integer.class));
        });
    }

    @Test
    void failedHistoryInsertRollsBackVehicleVersionAndSuccessfulDataSurvivesNewRepositoryInstances() {
        UUID id = insert(dealer, "Toyota", "Corolla", 100, 120);
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> transaction.executeWithoutResult(status ->
                actions.logAction(dealer, id, 0, VehicleActionType.MAINTENANCE_PLANNED, VehicleActionStatus.CREATED, null, now)
            ));
            assertEquals(0, search(dealer, 0, 20, null, null, null, null, "stockedAt", "desc").getFirst().rowVersion());
            transaction.executeWithoutResult(status -> actions.logAction(
                dealer, id, 0, VehicleActionType.MAINTENANCE_PLANNED, VehicleActionStatus.CREATED, UUID.randomUUID(), now
            ));
            var reloaded = new GetVehiclesRepository(jdbc).findVehicles(dealer, 0, 20, null, null, null, null, "stockedAt", "desc", now).getFirst();
            assertEquals(1, reloaded.rowVersion());
            assertEquals(VehicleActionType.MAINTENANCE_PLANNED, reloaded.action());
        } finally {
            jdbc.update("DELETE FROM public.vehicle_action WHERE vehicle_id=:id", Map.of("id", id));
            jdbc.update("DELETE FROM public.vehicle WHERE id=:id", Map.of("id", id));
        }
    }

    private List<VehicleView> search(
        String dealershipId,
        int page,
        int size,
        List<String> makes,
        List<String> models,
        Integer age,
        Boolean aging,
        String sortKey,
        String sortOrder
    ) {
        return vehicles.findVehicles(dealershipId, page, size, makes, models, age, aging, sortKey, sortOrder, now);
    }

    private UUID insert(
        String dealershipId,
        String make,
        String model,
        long price,
        int days
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            INSERT INTO public.vehicle (id, dealership_id, make, model, status, price, currency, stocked_at, version)
            VALUES (:id, :dealer, :make, :model, 'AVAILABLE', :price, 'USD', :stockedAt, 0)
            """,
            Map.of("id", id, "dealer", dealershipId, "make", make, "model", model,
                    "price", price, "stockedAt", Timestamp.from(now.minus(days, ChronoUnit.DAYS)))
        );
        return id;
    }

    private String env(
        String name,
        String fallback
    ) {
        return System.getenv().getOrDefault(name, fallback);
    }
}
