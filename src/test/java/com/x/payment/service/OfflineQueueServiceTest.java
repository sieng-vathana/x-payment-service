package com.x.payment.service;

import com.x.payment.dto.OfflineQueueReport;
import com.x.payment.entity.PosOfflineQueue;
import com.x.payment.repository.PosOfflineQueueRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfflineQueueServiceTest {

    private final PosOfflineQueueRepository repository = mock(PosOfflineQueueRepository.class);
    private final OfflineQueueService service = new OfflineQueueService(repository);

    @Test
    void aDeviceReportIsSavedWithItsCountsAndCurrency() {
        when(repository.save(any(PosOfflineQueue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PosOfflineQueue saved = service.report("pos-7f3a", new OfflineQueueReport(
                2L, 3L, 7L, "usd", 2, new BigDecimal("37.5"), 1));

        assertThat(saved.getDeviceId()).isEqualTo("pos-7f3a");
        assertThat(saved.getBusinessId()).isEqualTo(2L);
        assertThat(saved.getCurrencyCode()).isEqualTo("USD");
        assertThat(saved.getWaitingCount()).isEqualTo(2);
        assertThat(saved.getWaitingTotal()).isEqualByComparingTo("37.50");
        assertThat(saved.getAttentionCount()).isEqualTo(1);
        assertThat(saved.getReportedAt()).isNotNull();
    }

    @Test
    void aReportWithoutADeviceIdIsRefused() {
        assertThatThrownBy(() -> service.report("  ", new OfflineQueueReport(2L, 3L, null, "USD", 0, null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
