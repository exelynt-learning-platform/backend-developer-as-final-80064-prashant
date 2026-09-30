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

    public static final long MINUTES_PER_HOUR = 60L;
    public static final long ALLOWED_CLOCK_SKEW_SECONDS = 60L;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String SORT_ALIAS_PRICE = "price";
    public static final String TARGET_SORT_FIELD_PRICE = "totalPrice";
    public static final String DEFAULT_SORT_FIELD = "createdAt";
    public static final List<String> ALLOWED_SORT_FIELDS = List.of(
            "createdAt", "startTime", "endTime", "totalPrice", "id", "status"
    );

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
        User user = resolveUser(currentUsername);
        Resource resource = resolveResourceWithLock(request.getResourceId());

        if (!resource.isAvailable()) {
            throw new BadRequestException("Resource '" + resource.getName() + "' is not available for booking");
        }

        validateTimes(request.getStartTime(), request.getEndTime(), false);
        ensureNoOverlap(resource.getId(), request.getStartTime(), request.getEndTime(), null, resource.getName());

        BigDecimal totalPrice = calculateTotalPrice(isAdmin, request, resource);

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
        // Enforce pagination boundaries against negative indices or unbounded DoS requests
        if (filter.getPage() < 0) {
            throw new BadRequestException("Page index must not be negative");
        }
        if (filter.getSize() < 1 || filter.getSize() > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        Long userId = null;
        if (!isAdmin) {
            User user = resolveUser(currentUsername);
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

        // Whitelist validation with deterministic error output
        if (!ALLOWED_SORT_FIELDS.contains(property)) {
            throw new BadRequestException("Invalid sort field: '" + property + "'. Allowed sort fields are: " + ALLOWED_SORT_FIELDS);
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

        // Acquire pessimistic lock on the underlying resource to prevent status-transition race condition
        Resource resource = resolveResourceWithLock(reservation.getResource().getId());

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
            // Admin is updating status; if changing to active status from CANCELLED, verify overlap under lock
            if (reservation.getStatus() == ReservationStatus.CANCELLED && newStatus != ReservationStatus.CANCELLED) {
                ensureNoOverlap(resource.getId(), reservation.getStartTime(), reservation.getEndTime(),
                        reservation.getId(), resource.getName());
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

        Resource resource = resolveResourceWithLock(request.getResourceId());

        validateTimes(request.getStartTime(), request.getEndTime(), true);
        ensureNoOverlap(resource.getId(), request.getStartTime(), request.getEndTime(), reservation.getId(), resource.getName());

        reservation.setResource(resource);
        reservation.setStartTime(request.getStartTime());
        reservation.setEndTime(request.getEndTime());
        if (request.getPrice() != null && request.getPrice().compareTo(BigDecimal.ZERO) >= 0) {
            reservation.setTotalPrice(request.getPrice().setScale(2, RoundingMode.HALF_UP));
        } else {
            reservation.setTotalPrice(calculateBillablePrice(resource, request.getStartTime(), request.getEndTime()));
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

    // --- Private Helper Methods (SRP, DRY & Maintainability) ---

    private User resolveUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("Authenticated user not found: " + username));
    }

    private Resource resolveResourceWithLock(Long resourceId) {
        return resourceRepository.findByIdWithLock(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + resourceId));
    }

    private void ensureNoOverlap(Long resourceId, LocalDateTime start, LocalDateTime end, Long excludeReservationId, String resourceName) {
        List<Reservation> conflicts;
        if (excludeReservationId != null) {
            conflicts = reservationRepository.findOverlappingReservationsExcludingId(
                    resourceId, excludeReservationId, start, end, ReservationStatus.CANCELLED
            );
        } else {
            conflicts = reservationRepository.findOverlappingReservations(
                    resourceId, start, end, ReservationStatus.CANCELLED
            );
        }

        if (!conflicts.isEmpty()) {
            throw new ReservationConflictException(
                    "Resource '" + resourceName + "' is already reserved during the requested time window (" +
                            start + " to " + end + ")"
            );
        }
    }

    private BigDecimal calculateTotalPrice(boolean isAdmin, ReservationRequest request, Resource resource) {
        if (request.getPrice() != null) {
            if (!isAdmin) {
                throw new BadRequestException("Only administrators can specify custom reservation prices. Standard rates apply for regular users.");
            }
            if (request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Reservation price cannot be negative");
            }
            return request.getPrice().setScale(2, RoundingMode.HALF_UP);
        }
        return calculateBillablePrice(resource, request.getStartTime(), request.getEndTime());
    }

    private BigDecimal calculateBillablePrice(Resource resource, LocalDateTime startTime, LocalDateTime endTime) {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        long billableHours = Math.max(1L, (minutes + MINUTES_PER_HOUR - 1) / MINUTES_PER_HOUR);
        return resource.getBasePrice()
                .multiply(BigDecimal.valueOf(billableHours))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateTimes(LocalDateTime startTime, LocalDateTime endTime, boolean allowPast) {
        if (startTime == null || endTime == null) {
            throw new BadRequestException("Start time and end time are required");
        }

        if (!startTime.isBefore(endTime)) {
            throw new BadRequestException("Start time must be before end time");
        }

        // Allow configured tolerance for clock skew on creation
        if (!allowPast && startTime.isBefore(LocalDateTime.now().minusSeconds(ALLOWED_CLOCK_SKEW_SECONDS))) {
            throw new BadRequestException("Start time cannot be in the past");
        }
    }
}
