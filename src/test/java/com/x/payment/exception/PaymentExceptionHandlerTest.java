package com.x.payment.exception;

import com.sharedlib.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentExceptionHandlerTest {
    private final PaymentExceptionHandler handler = new PaymentExceptionHandler();

    @Test
    void preservesKhqrPayRejectionStatusAndMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatus(
                new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "KHQRPay rejected checkout: Invalid Security Hash"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(-1);
        assertThat(response.getBody().getCode()).isEqualTo(502);
        assertThat(response.getBody().getMessage())
                .isEqualTo("KHQRPay rejected checkout: Invalid Security Hash");
    }
}
