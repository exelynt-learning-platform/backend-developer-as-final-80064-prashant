package com.exelynt.booking.service;

import com.exelynt.booking.dto.PaginatedResponse;
import com.exelynt.booking.dto.ReservationQueryFilter;
import com.exelynt.booking.dto.ReservationRequest;
import com.exelynt.booking.dto.ReservationResponse;
import com.exelynt.booking.entity.Reservation;
import com.exelynt.booking.entity.ReservationStatus;
import com.exelynt.booking.entity.Resource;
import com.exelynt.booking.entity.User;
import com.exelynt.booking.exception.BadRequestException;
import com.exelynt.booking.exception.ReservationConflictException;
import com.exelynt.booking.exception.ResourceNotFoundException;
import com.exelynt.booking.repository.ReservationRepository;
import com.exelynt.booking.repository.ReservationSpecification;
import com.exelynt.booking.repository.ResourceRepository;
import com.exelynt.booking.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService {

    public static final String SORT_ALIAS_PRICE = "price";
    public static final String TARGET_SORT_FIELD_PRICE = "totalPrice";
    public static final String DEFAULT_SORT_FIELD = "createdAt";

    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              ResourceRepository resourceRepository,
                              UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReservationResponse createReservation(ReservationRequest request, String currentUsername, boolean isAdmin) {
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new BadRequestException("Authenticated user not found: " + currentUsername));

        // Pessimistic lock prevents concurrent double-booking of the same resource
        Resource resource = resourceRepository.findByIdWithLock(request.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + request.getResourceId()));

        if (!resource.isAvailable()) {
            throw new BadRequestException("Resource '" + resource.getName() + "' is not available for booking");
        }

        validateTimesForCreation(request.getStartTime(), request.getEndTime());

        // Check for conflicting reservations on the same resource
        List<Reservation> conflicts = reservationRepository.findOverlappingReservations(
                resource.getId(),
                request.getStartTime(),
                request.getEndTime(),
                ReservationStatus.CANCELLED
        );

        if (!conflicts.isEmpty()) {
            throw new ReservationConflictException(
                    "Resource '" + resource.getName() + "' is already reserved during the requested time window (" +
                            request.getStartTime() + " to " + request.getEndTime() + ")"
            );
        }

        // Price calculation: Only ADMIN can supply manual price overrides to prevent user manipulation
        BigDecimal totalPrice;
        if (isAdmin && request.getPrice() != null && request.getPrice().compareTo(BigDecimal.ZERO) >= 0) {
            totalPrice = request.getPrice().setScale(2, RoundingMode.HALF_UP);
        } else {
            long minutes = Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
            long billableHours = Math.max(1L, (minutes + 59) / 60); // Round up to nearest whole hour, min 1
            totalPrice = resource.getBasePrice()
                    .multiply(BigDecimal.valueOf(billableHours))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        Reservation reservation = new Reservation(
                user,
                resource,
                request.getStartTime(),
                request.getEndTime(),
                ReservationStatus.CONFIRMED,
                totalPrice,
                request.getNotes()
        );

        Reservation saved = reservationRepository.save(reservation);
        return ReservationResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<ReservationResponse> getReservations(
            ReservationQueryFilter filter,
            String currentUsername,
            boolean isAdmin
    ) {
        Long userId = null;
        if (!isAdmin) {
            User user = userRepository.findByUsername(currentUsername)
                    .orElseThrow(() -> new BadRequestException("Authenticated user not found: " + currentUsername));
            userId = user.getId();
        }

        Sort.Direction direction = "desc".equalsIgnoreCase(filter.getSortDir()) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String property = (filter.getSortBy() != null && !filter.getSortBy().trim().isEmpty())
                ? filter.getSortBy().trim()
                : DEFAULT_SORT_FIELD;

        // Map client friendly sort property aliases
        if (SORT_ALIAS_PRICE.equalsIgnoreCase(property)) {
            property = TARGET_SORT_FIELD_PRICE;
        }

        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by(direction, property));

        Specification<Reservation> spec = ReservationSpecification.filterReservations(
                userId, filter.getStatus(), filter.getMinPrice(), filter.getMaxPrice()
        );
        Page<Reservation> reservationPage = reservationRepository.findAll(spec, pageable);

        Page<ReservationResponse> responsePage = reservationPage.map(ReservationResponse::fromEntity);
        return PaginatedResponse.fromPage(responsePage);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long id, String currentUsername, boolean isAdmin) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        if (!isAdmin && !reservation.getUser().getUsername().equals(currentUsername)) {
            throw new AccessDeniedException("Access Denied: You can only view your own reservations");
        }

        return ReservationResponse.fromEntity(reservation);
    }

    @Transactional
    public ReservationResponse updateReservationStatus(Long id, ReservationStatus newStatus, String currentUsername, boolean isAdmin) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        if (!isAdmin) {
            // Regular user can only access their own reservation
            if (!reservation.getUser().getUsername().equals(currentUsername)) {
                throw new AccessDeniedException("Access Denied: You can only manage your own reservations");
            }
            // Regular user can only cancel their reservation
            if (newStatus != ReservationStatus.CANCELLED) {
                throw new BadRequestException("Users are only permitted to cancel their reservations");
            }
        } else {
            // Admin is updating status; if changing to active status from CANCELLED, verify overlap
            if (reservation.getStatus() == ReservationStatus.CANCELLED && newStatus != ReservationStatus.CANCELLED) {
                List<Reservation> conflicts = reservationRepository.findOverlappingReservationsExcludingId(
                        reservation.getResource().getId(),
                        reservation.getId(),
                        reservation.getStartTime(),
                        reservation.getEndTime(),
                        ReservationStatus.CANCELLED
                );
                if (!conflicts.isEmpty()) {
                    throw new ReservationConflictException("Cannot reactivate reservation: Conflicting reservation exists");
                }
            }
        }

        reservation.setStatus(newStatus);
        Reservation updated = reservationRepository.save(reservation);
        return ReservationResponse.fromEntity(updated);
    }

    @Transactional
    public ReservationResponse updateReservation(Long id, ReservationRequest request) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        Resource resource = resourceRepository.findByIdWithLock(request.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + request.getResourceId()));

        validateTimesForUpdate(request.getStartTime(), request.getEndTime());

        List<Reservation> conflicts = reservationRepository.findOverlappingReservationsExcludingId(
                resource.getId(),
                reservation.getId(),
                request.getStartTime(),
                request.getEndTime(),
                ReservationStatus.CANCELLED
        );

        if (!conflicts.isEmpty()) {
            throw new ReservationConflictException("Resource is already reserved for the specified time range");
        }

        reservation.setResource(resource);
        reservation.setStartTime(request.getStartTime());
        reservation.setEndTime(request.getEndTime());
        if (request.getPrice() != null && request.getPrice().compareTo(BigDecimal.ZERO) >= 0) {
            reservation.setTotalPrice(request.getPrice().setScale(2, RoundingMode.HALF_UP));
        } else {
            long minutes = Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
            long billableHours = Math.max(1L, (minutes + 59) / 60);
            reservation.setTotalPrice(resource.getBasePrice().multiply(BigDecimal.valueOf(billableHours)).setScale(2, RoundingMode.HALF_UP));
        }
        reservation.setNotes(request.getNotes());

        Reservation updated = reservationRepository.save(reservation);
        return ReservationResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteReservation(Long id) {
        if (!reservationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reservation not found with id: " + id);
        }
        reservationRepository.deleteById(id);
    }

    private void validateTimesForCreation(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BadRequestException("Start time and end time are required");
        }

        if (!startTime.isBefore(endTime)) {
            throw new BadRequestException("Start time must be before end time");
        }

        // Allow 60 seconds clock skew tolerance for validation
        if (startTime.isBefore(LocalDateTime.now().minusSeconds(60))) {
            throw new BadRequestException("Start time cannot be in the past");
        }
    }

    private void validateTimesForUpdate(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BadRequestException("Start time and end time are required");
        }

        if (!startTime.isBefore(endTime)) {
            throw new BadRequestException("Start time must be before end time");
        }
    }
}
