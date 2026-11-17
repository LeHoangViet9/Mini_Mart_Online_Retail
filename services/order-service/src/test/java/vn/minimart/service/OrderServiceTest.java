package vn.minimart.service;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import vn.minimart.client.CatalogClient;
import vn.minimart.client.CustomerClient;
import vn.minimart.client.ShippingClient;
import vn.minimart.dto.PlaceOrderRequest;
import vn.minimart.model.OrderStatus;
import vn.minimart.model.Zone;
import vn.minimart.repository.OrderRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository repository;
    @Mock CatalogClient catalog;
    @Mock CustomerClient customer;
    @Mock ShippingClient shipping;
    @InjectMocks OrderService service;

    private final PlaceOrderRequest request = new PlaceOrderRequest(1, 1, 2, "Demo", Zone.OUTSIDE);

    @Test
    void uncertainReservationKeepsPendingAndDoesNotDebitOrRelease() {
        when(catalog.reserve(any(), eq(1L), eq(2))).thenThrow(new ResourceAccessException("timeout"));

        var exception = catchThrowableOfType(ResponseStatusException.class, () -> service.place(request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exception.getReason()).startsWith("RECONCILIATION_REQUIRED orderId=");
        verify(repository).insert(argThat(order -> order.status() == OrderStatus.PENDING));
        verify(repository, never()).updateOutcome(any(), any(), anyBoolean(), any());
        verify(catalog, never()).release(any());
        verifyNoInteractions(customer, shipping);
    }

    @Test
    void definiteDebitRejectionReleasesStockAndCancelsWithoutRefund() {
        when(catalog.reserve(any(), eq(1L), eq(2))).thenReturn(50000L);
        doThrow(new HttpClientErrorException(HttpStatus.CONFLICT)).when(customer).debit(any(), eq(1L), eq(50000L));

        var exception = catchThrowableOfType(ResponseStatusException.class, () -> service.place(request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).startsWith("BUSINESS_REJECTED orderId=");
        verify(catalog).release(any());
        verify(repository).updateOutcome(any(), eq(OrderStatus.CANCELLED), eq(false), eq("BUSINESS_REJECTED"));
        verify(customer, never()).refund(any());
        verifyNoInteractions(shipping);
    }

    @Test
    void uncertainShipmentAfterDebitKeepsPendingForReconciliation() {
        when(catalog.reserve(any(), eq(1L), eq(2))).thenReturn(50000L);
        doThrow(new ResourceAccessException("timeout")).when(shipping).create(any(), eq("Demo"), eq(Zone.OUTSIDE));

        var exception = catchThrowableOfType(ResponseStatusException.class, () -> service.place(request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(customer).debit(any(), eq(1L), eq(50000L));
        verify(customer, never()).refund(any());
        verify(catalog, never()).release(any());
        verify(repository, never()).updateOutcome(any(), any(), anyBoolean(), any());
    }

    @Test
    void failedRefundMustNotReportCancelledOrReleaseStock() {
        when(catalog.reserve(any(), eq(1L), eq(2))).thenReturn(50000L);
        var rejection = HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY, "Rejected",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        doThrow(rejection).when(shipping).create(any(), eq("Demo"), eq(Zone.OUTSIDE));
        doThrow(new ResourceAccessException("refund timeout")).when(customer).refund(any());

        var exception = catchThrowableOfType(ResponseStatusException.class, () -> service.place(request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(customer).refund(any());
        verify(catalog, never()).release(any());
        verify(repository, never()).updateOutcome(any(), any(), anyBoolean(), any());
    }
}
