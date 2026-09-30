package com.exelynt.booking.repository;

import com.exelynt.booking.entity.Reservation;
import com.exelynt.booking.entity.ReservationStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ReservationSpecification {

    public static Specification<Reservation> filterReservations(
            Long userId,
            ReservationStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("id"), userId));
            }

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (minPrice != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("totalPrice"), minPrice));
            }

            if (maxPrice != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("totalPrice"), maxPrice));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
