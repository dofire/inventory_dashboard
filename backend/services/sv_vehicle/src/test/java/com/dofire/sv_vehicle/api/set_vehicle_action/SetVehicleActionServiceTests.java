package com.dofire.sv_vehicle.api.set_vehicle_action;

import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import com.dofire.sv_vehicle.global.model.VehicleActionType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SetVehicleActionServiceTests {
    private final SetVehicleActionRepository repository = mock(SetVehicleActionRepository.class);
    private final SetVehicleActionService service = new SetVehicleActionService(repository);

    @Test
    void omittedActionStatusDefaultsToCreated() {
        var request = request();
        service.updateStatus(request);
        verifySaved(request, VehicleActionStatus.CREATED);
    }

    @ParameterizedTest
    @EnumSource(VehicleActionStatus.class)
    void explicitActionStatusAndIdentityArePreserved(VehicleActionStatus status) {
        var request = request();
        request.setActionStatus(status);
        service.updateStatus(request);
        verifySaved(request, status);
    }

    @Test
    void staleVersionIsPropagatedToTheController() {
        var request = request();
        var conflict = new ResponseStatusException(HttpStatus.CONFLICT);
        doThrow(conflict).when(repository).logAction(anyString(), any(), anyLong(), any(), any(), any(), any());
        assertSame(conflict, assertThrows(ResponseStatusException.class, () -> service.updateStatus(request)));
    }

    private UpdateStatusRequest request() {
        var request = new UpdateStatusRequest();
        request.setDealershipId("DEALER_HCM_001");
        request.setVehicleId(UUID.randomUUID());
        request.setUserId(UUID.randomUUID());
        request.setRowVersion(7L);
        request.setStatus(VehicleActionType.MAINTENANCE_PLANNED);
        return request;
    }

    private void verifySaved(
        UpdateStatusRequest request,
        VehicleActionStatus status
    ) {
        verify(repository).logAction(
            eq(request.getDealershipId()),
            eq(request.getVehicleId()),
            eq(request.getRowVersion()),
            eq(request.getStatus()),
            eq(status),
            eq(request.getUserId()),
            any(Instant.class)
        );
    }
}
