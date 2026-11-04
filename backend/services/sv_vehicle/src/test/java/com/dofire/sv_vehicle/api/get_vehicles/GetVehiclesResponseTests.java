package com.dofire.sv_vehicle.api.get_vehicles;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class GetVehiclesResponseTests {
    @ParameterizedTest
    @CsvSource({"0,20,0", "1,20,1", "20,20,1", "21,20,2", "40,20,2"})
    void paginationHandlesEmptyExactAndPartialLastPages(
        long totalItems,
        int size,
        long expectedPages
    ) {
        var response = new GetVehiclesResponse(new VehiclePage(
            List.of(),
            totalItems,
            0,
            size
        ));
        assertTrue(response.getData().isEmpty());
        assertEquals(totalItems, response.getPagination().totalItems());
        assertEquals(size, response.getPagination().size());
        assertEquals(expectedPages, response.getPagination().totalPages());
    }
}
