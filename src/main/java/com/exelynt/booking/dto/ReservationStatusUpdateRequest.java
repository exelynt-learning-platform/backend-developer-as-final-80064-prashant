package com.exelynt.booking.dto;

import com.exelynt.booking.entity.ReservationStatus;
import jakarta.validation.constraints.NotNull;

public class ReservationStatusUpdateRequest {

    @NotNull(message = "Reservation status is required (PENDING, CONFIRMED, CANCELLED)")
    private ReservationStatus status;

    public ReservationStatusUpdateRequest() {
    }

    public ReservationStatusUpdateRequest(ReservationStatus status) {
        this.status = status;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }
}
