package com.exelynt.booking.controller;

import com.exelynt.booking.dto.PaginatedResponse;
import com.exelynt.booking.dto.ReservationRequest;
import com.exelynt.booking.dto.ReservationResponse;
import com.exelynt.booking.dto.ReservationStatusUpdateRequest;
import com.exelynt.booking.entity.ReservationStatus;
import com.exelynt.booking.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Endpoints for creating, managing, filtering, and viewing reservations")
@SecurityRequirement(name = "Bearer Authentication")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Create a reservation (USER & ADMIN)",
            description = "Creates a reservation for a bookable resource. The user's identity is strictly extracted from the JWT token.")
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody ReservationRequest request,
            Authentication authentication
    ) {
        String currentUsername = authentication.getName();
        ReservationResponse response = reservationService.createReservation(request, currentUsername);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Get reservations with filtering, pagination, and sorting",
            description = "ADMINs see all reservations across all users. Regular USERs see only their own reservations. Supports filtering by status, minPrice, maxPrice, and pagination/sorting.")
    public ResponseEntity<PaginatedResponse<ReservationResponse>> getReservations(
            @Parameter(description = "Filter by reservation status (PENDING, CONFIRMED, CANCELLED)")
            @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "Filter by minimum price (decimal)")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Filter by maximum price (decimal)")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Field to sort by (e.g., createdAt, totalPrice, startTime)")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc or desc)")
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication
    ) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));

        PaginatedResponse<ReservationResponse> response = reservationService.getReservations(
                authentication.getName(),
                isAdmin,
                status,
                minPrice,
                maxPrice,
                page,
                size,
                sortBy,
                sortDir
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Get reservation details by ID",
            description = "Retrieves reservation details. ADMINs can view any reservation. Regular USERs can only view their own.")
    public ResponseEntity<ReservationResponse> getReservationById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));

        ReservationResponse response = reservationService.getReservationById(id, authentication.getName(), isAdmin);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Update reservation status",
            description = "USERs can cancel their own reservation (status=CANCELLED). ADMINs can update to any valid status (PENDING, CONFIRMED, CANCELLED).")
    public ResponseEntity<ReservationResponse> updateStatusViaPatch(
            @PathVariable Long id,
            @Valid @RequestBody ReservationStatusUpdateRequest request,
            Authentication authentication
    ) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));

        ReservationResponse response = reservationService.updateReservationStatus(
                id,
                request.getStatus(),
                authentication.getName(),
                isAdmin
        );
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Update reservation status (PUT alternative)",
            description = "Alternative endpoint for status update. USERs can cancel their own reservation; ADMINs can set any status.")
    public ResponseEntity<ReservationResponse> updateStatusViaPut(
            @PathVariable Long id,
            @Valid @RequestBody ReservationStatusUpdateRequest request,
            Authentication authentication
    ) {
        return updateStatusViaPatch(id, request, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update full reservation details (ADMIN only)",
            description = "Updates resource, timing, price, and notes for an existing reservation.")
    public ResponseEntity<ReservationResponse> updateReservation(
            @PathVariable Long id,
            @Valid @RequestBody ReservationRequest request
    ) {
        ReservationResponse response = reservationService.updateReservation(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a reservation (ADMIN only)",
            description = "Deletes a reservation permanently.")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        reservationService.deleteReservation(id);
        return ResponseEntity.noContent().build();
    }
}
