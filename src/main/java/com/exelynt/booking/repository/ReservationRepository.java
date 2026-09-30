package com.exelynt.booking.repository;

import com.exelynt.booking.entity.Reservation;
import com.exelynt.booking.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    @Query("SELECT r FROM Reservation r WHERE r.resource.id = :resourceId " +
           "AND r.status <> :excludedStatus " +
           "AND r.startTime < :endTime AND r.endTime > :startTime")
    List<Reservation> findOverlappingReservations(
            @Param("resourceId") Long resourceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludedStatus") ReservationStatus excludedStatus
    );

    @Query("SELECT r FROM Reservation r WHERE r.resource.id = :resourceId " +
           "AND r.id <> :excludeReservationId " +
           "AND r.status <> :excludedStatus " +
           "AND r.startTime < :endTime AND r.endTime > :startTime")
    List<Reservation> findOverlappingReservationsExcludingId(
            @Param("resourceId") Long resourceId,
            @Param("excludeReservationId") Long excludeReservationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludedStatus") ReservationStatus excludedStatus
    );

    List<Reservation> findByUserId(Long userId);
}
