package com.dofire.sv_vehicle.api;

import com.dofire.sv_vehicle.api.get_vehicle_filters.GetVehicleFiltersRepository;
import com.dofire.sv_vehicle.api.get_vehicles.GetVehiclesRepository;
import com.dofire.sv_vehicle.api.get_vehicles.VehicleView;
import com.dofire.sv_vehicle.api.set_vehicle_action.SetVehicleActionRepository;
import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import com.dofire.sv_vehicle.global.model.VehicleActionType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MvcResult;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/** Full application wiring against an isolated PostgreSQL instance, without an HTTP server. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Testcontainers
class VehicleApiIntegrationTests {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17.6-alpine");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private GetVehiclesRepository vehicles;
    @Autowired
    private GetVehicleFiltersRepository filters;
    @Autowired
    private SetVehicleActionRepository actions;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private Flyway flyway;
    private TransactionTemplate transaction;
    private final String dealer = "TEST_" + UUID.randomUUID();
    private final String otherDealer = "TEST_" + UUID.randomUUID();
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @BeforeEach
    void resetDatabase() {
        jdbc.getJdbcTemplate().execute("TRUNCATE TABLE public.vehicle_action, public.vehicle");
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void applicationMigratesAndValidatesAnEmptyPostgresDatabase() {
        flyway.validate();
        assertEquals("2", flyway.info().current().getVersion().getVersion());
        assertEquals(0, jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM public.vehicle", Integer.class));
        assertEquals(0, jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM public.vehicle_action", Integer.class));
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
            assertThrows(DataIntegrityViolationException.class, () -> transaction.executeWithoutResult(status ->
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

    @Test
    void inventoryHttpResponseHasMatchingTotalsAndCorrelationIds() throws Exception {
        insert("DEALER_HCM_001", "Toyota", "Corolla", 100, 120);
        insert("DEALER_HCM_001", "Toyota", "Camry", 200, 95);
        insert("DEALER_HN_002", "Mazda", "CX-5", 50, 180);
        String requestId = UUID.randomUUID().toString();

        var result = mvc.perform(get("/vehicles")
                .param("dealershipId", "DEALER_HCM_001")
                .param("make", "TOYOTA")
                .param("page", "1")
                .param("size", "1")
                .param("sortKey", "price")
                .param("sortOrder", "asc")
                .header("X-Request-ID", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].model").value("Camry"))
                .andExpect(jsonPath("$.data[0].dealershipId").value("DEALER_HCM_001"))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.size").value(1))
                .andExpect(jsonPath("$.pagination.totalItems").value(2))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andReturn();
        assertCorrelation(result);

        mvc.perform(get("/vehicles").param("dealershipId", "DEALER_HCM_001").param("page", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.totalItems").value(2));
        mvc.perform(get("/vehicles").param("dealershipId", "DEALER_HN_002").param("make", "Toyota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.totalItems").value(0))
                .andExpect(jsonPath("$.pagination.totalPages").value(0));
    }

    @Test
    void filterOptionsOnlyIncludeTheRequestedDealership() throws Exception {
        insert("DEALER_HCM_001", "Toyota", "Corolla", 100, 120);
        insert("DEALER_HN_002", "Mazda", "CX-5", 50, 180);
        mvc.perform(get("/vehicle-filters").param("dealershipId", "DEALER_HN_002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.makes.Mazda[0]").value("CX-5"))
                .andExpect(jsonPath("$.data.makes.Toyota").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101", "age,-1", "sortKey,unknown", "sortOrder,sideways"})
    void invalidInventoryParametersReturnCorrelatedBadRequests(
        String parameter,
        String value
    ) throws Exception {
        var result = mvc.perform(get("/vehicles").param("dealershipId", "DEALER_HCM_001").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data").isString())
                .andReturn();
        assertCorrelation(result);
    }

    @Test
    void everyBusinessEndpointRejectsMissingOrUnsupportedDealerships() throws Exception {
        for (String endpoint : List.of("/vehicles", "/vehicle-filters")) {
            mvc.perform(get(endpoint)).andExpect(status().isBadRequest());
            mvc.perform(get(endpoint).param("dealershipId", "UNKNOWN")).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/login").contentType("application/json").content("{\"account\":\"tony\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/update-vehicle-status").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsTheSelectedProfileAndUnknownAccountsAreUnauthorized() throws Exception {
        var result = mvc.perform(post("/login").contentType("application/json").content("""
                {"account":" TONY ","dealershipId":"DEALER_HN_002"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account").value("tony"))
                .andExpect(jsonPath("$.data.dealershipId").value("DEALER_HN_002"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andReturn();
        assertCorrelation(result);
        mvc.perform(post("/login").contentType("application/json").content("""
                {"account":"unknown","dealershipId":"DEALER_HN_002"}
                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").isString());
    }

    @Test
    void actionHttpRequestsPersistAndReturnConflictOrNotFoundWithoutExtraHistory() throws Exception {
        UUID vehicleId = insert("DEALER_HCM_001", "Toyota", "Corolla", 100, 120);
        String body = actionBody(vehicleId, "DEALER_HCM_001");
        var saved = mvc.perform(post("/update-vehicle-status").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andReturn();
        assertCorrelation(saved);
        mvc.perform(get("/vehicles").param("dealershipId", "DEALER_HCM_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rowVersion").value(1))
                .andExpect(jsonPath("$.data[0].action").value("MAINTENANCE_PLANNED"))
                .andExpect(jsonPath("$.data[0].actionStatus").value("CREATED"));
        var conflict = mvc.perform(post("/update-vehicle-status").contentType("application/json").content(body))
                .andExpect(status().isConflict()).andReturn();
        assertCorrelation(conflict);
        mvc.perform(post("/update-vehicle-status").contentType("application/json")
                .content(actionBody(vehicleId, "DEALER_HN_002")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/update-vehicle-status").contentType("application/json")
                .content(actionBody(UUID.randomUUID(), "DEALER_HCM_001")))
                .andExpect(status().isNotFound());
        assertEquals(1, jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM public.vehicle_action", Integer.class));
    }

    @Test
    void concurrentWritersWithTheSameVersionProduceExactlyOneHistoryEntry() throws Exception {
        UUID id = insert("DEALER_HCM_001", "Toyota", "Corolla", 100, 120);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Integer> write = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for concurrent writers");
            try {
                actions.logAction("DEALER_HCM_001", id, 0, VehicleActionType.MAINTENANCE_PLANNED, VehicleActionStatus.CREATED, UUID.randomUUID(), now);
                return 200;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(write);
            var second = executor.submit(write);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), outcomes.stream().sorted().toList());
        } finally {
            start.countDown();
        }
        assertEquals(1, jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM public.vehicle_action", Integer.class));
        assertEquals(1, search("DEALER_HCM_001", 0, 20, null, null, null, null, "stockedAt", "desc").getFirst().rowVersion());
    }

    @Test
    void vehicleHasManyHistoryEntriesAndRemovingTheProposalClearsBothFields() throws Exception {
        UUID id = insert("DEALER_HCM_001", "Toyota", "Corolla", 100, 120);
        String first = actionBody(id, "DEALER_HCM_001");
        mvc.perform(post("/update-vehicle-status").contentType("application/json").content(first))
                .andExpect(status().isOk());
        mvc.perform(post("/update-vehicle-status").contentType("application/json")
                .content(first.replace("\"rowVersion\":0", "\"rowVersion\":1").replace("MAINTENANCE_PLANNED", "PRICE_REDUCTION_PLANNED")))
                .andExpect(status().isOk());
        mvc.perform(get("/vehicles/" + id + "/actions").param("dealershipId", "DEALER_HCM_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].rowVersion").value(2))
                .andExpect(jsonPath("$.data[1].action").value("MAINTENANCE_PLANNED"));
        String remove = """
                {"vehicleId":"%s","dealershipId":"DEALER_HCM_001",
                 "userId":"11111111-1111-4111-8111-111111111111","rowVersion":2}
                """.formatted(id);
        mvc.perform(post("/remove-vehicle-action").contentType("application/json").content(remove))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(nullValue()));
        mvc.perform(get("/vehicles").param("dealershipId", "DEALER_HCM_001"))
                .andExpect(jsonPath("$.data[0].action").value(nullValue()))
                .andExpect(jsonPath("$.data[0].actionStatus").value(nullValue()))
                .andExpect(jsonPath("$.data[0].rowVersion").value(3));
        mvc.perform(get("/vehicles/" + id + "/actions").param("dealershipId", "DEALER_HCM_001").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(3))
                .andExpect(jsonPath("$.data[0].removed").value(true))
                .andExpect(jsonPath("$.data[0].actionStatus").value(nullValue()));
        mvc.perform(get("/vehicles/" + id + "/actions").param("dealershipId", "DEALER_HN_002"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/remove-vehicle-action").contentType("application/json").content(remove))
                .andExpect(status().isConflict());
        mvc.perform(post("/remove-vehicle-action").contentType("application/json")
                .content(remove.replace("\"rowVersion\":2", "\"rowVersion\":3")))
                .andExpect(status().isNotFound());
        assertEquals(3, jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM public.vehicle_action", Integer.class));
        assertEquals(3, search("DEALER_HCM_001", 0, 20, null, null, null, null, "stockedAt", "desc").getFirst().rowVersion());
        mvc.perform(post("/update-vehicle-status").contentType("application/json")
                .content(first.replace("\"rowVersion\":0", "\"rowVersion\":3")))
                .andExpect(status().isOk());
        assertEquals(4, search("DEALER_HCM_001", 0, 20, null, null, null, null, "stockedAt", "desc").getFirst().rowVersion());
    }

    private String actionBody(
        UUID vehicleId,
        String dealershipId
    ) {
        return """
                {"dealershipId":"%s","vehicleId":"%s","userId":"11111111-1111-4111-8111-111111111111",
                 "rowVersion":0,"status":"MAINTENANCE_PLANNED"}
                """.formatted(dealershipId, vehicleId);
    }

    private void assertCorrelation(MvcResult result) throws Exception {
        String requestId = result.getResponse().getHeader("X-Request-ID");
        String traceId = result.getResponse().getHeader("X-Trace-ID");
        assertNotNull(requestId);
        assertNotNull(traceId);
        UUID.fromString(requestId);
        assertTrue(traceId.matches("[0-9a-f]{32}"));
        jsonPath("$.requestId").value(requestId).match(result);
        jsonPath("$.traceId").value(traceId).match(result);
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

}
