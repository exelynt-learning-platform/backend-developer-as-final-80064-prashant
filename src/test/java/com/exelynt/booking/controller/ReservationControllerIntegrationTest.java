package com.exelynt.booking.controller;

import com.exelynt.booking.dto.ReservationRequest;
import com.exelynt.booking.dto.ReservationStatusUpdateRequest;
import com.exelynt.booking.entity.*;
import com.exelynt.booking.repository.ReservationRepository;
import com.exelynt.booking.repository.ResourceRepository;
import com.exelynt.booking.repository.UserRepository;
import com.exelynt.booking.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private Resource testResource;
    private User testUser;
    private User testOtherUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByUsername("user").orElseThrow();
        testOtherUser = userRepository.findByUsername("john_doe").orElseThrow();
        testResource = resourceRepository.findAll().get(0);
    }

    @Test
    @WithUserDetails("user")
    @DisplayName("POST /api/reservations - USER creates reservation with identity extracted from JWT, price calculated server-side")
    void testCreateReservation() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(10).withMinute(0).withSecond(0);
        LocalDateTime end = LocalDateTime.now().plusDays(10).withHour(12).withMinute(0).withSecond(0);

        ReservationRequest request = new ReservationRequest(
                testResource.getId(),
                start,
                end,
                null,
                "Integration test booking"
        );

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.resourceId").value(testResource.getId()))
                .andExpect(jsonPath("$.totalPrice").value(100.00)) // 2 billable hours * 50.00 base rate
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithUserDetails("user")
    @DisplayName("POST /api/reservations - Regular USER cannot override price (400 Bad Request)")
    void testUserCannotOverridePrice() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(11).withHour(10).withMinute(0).withSecond(0);
        LocalDateTime end = LocalDateTime.now().plusDays(11).withHour(12).withMinute(0).withSecond(0);

        ReservationRequest request = new ReservationRequest(
                testResource.getId(),
                start,
                end,
                new BigDecimal("10.00"), // Attempted price manipulation
                "Tampered price booking"
        );

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only administrators can specify custom reservation prices. Standard rates apply for regular users."));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("POST /api/reservations - ADMIN creates reservation with custom price override")
    void testAdminCreateReservationWithCustomPrice() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(12).withHour(10).withMinute(0).withSecond(0);
        LocalDateTime end = LocalDateTime.now().plusDays(12).withHour(12).withMinute(0).withSecond(0);

        ReservationRequest request = new ReservationRequest(
                testResource.getId(),
                start,
                end,
                new BigDecimal("225.50"),
                "Admin custom priced booking"
        );

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.totalPrice").value(225.50));
    }

    @Test
    @DisplayName("POST /api/reservations - Real JWT token in Authorization header passes through JwtAuthenticationFilter")
    void testRealJwtTokenAuthenticationFilter() throws Exception {
        String token = tokenProvider.generateToken("user", testUser.getId(), "ROLE_USER");
        LocalDateTime start = LocalDateTime.now().plusDays(14).withHour(10).withMinute(0).withSecond(0);
        LocalDateTime end = LocalDateTime.now().plusDays(14).withHour(11).withMinute(0).withSecond(0);

        ReservationRequest request = new ReservationRequest(
                testResource.getId(),
                start,
                end,
                null,
                "Real token test booking"
        );

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.totalPrice").value(50.00));
    }

    @Test
    @WithUserDetails("user")
    @DisplayName("POST /api/reservations - Overlapping reservation triggers 409 Conflict")
    void testOverlappingReservationConflict() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(15).withHour(10).withMinute(0).withSecond(0);
        LocalDateTime end = LocalDateTime.now().plusDays(15).withHour(12).withMinute(0).withSecond(0);

        Reservation existing = new Reservation(
                testUser,
                testResource,
                start,
                end,
                ReservationStatus.CONFIRMED,
                new BigDecimal("100.00"),
                "Existing booking"
        );
        reservationRepository.save(existing);

        // Attempt overlapping reservation
        ReservationRequest conflictingRequest = new ReservationRequest(
                testResource.getId(),
                start.plusMinutes(30),
                end.plusMinutes(30),
                new BigDecimal("100.00"),
                "Conflicting booking"
        );

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictingRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @WithUserDetails("user")
    @DisplayName("POST /api/reservations - Invalid time range returns 400 Bad Request")
    void testInvalidTimeRange() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(20).withHour(14).withMinute(0).withSecond(0);
        LocalDateTime end = LocalDateTime.now().plusDays(20).withHour(12).withMinute(0).withSecond(0); // End before start

        ReservationRequest request = new ReservationRequest(
                testResource.getId(),
                start,
                end,
                new BigDecimal("100.00"),
                "Invalid time booking"
        );

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithUserDetails("user")
    @DisplayName("GET /api/reservations - USER sees only their own reservations")
    void testUserSeesOnlyOwnReservations() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[*].username", everyItem(is("user"))));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("GET /api/reservations - ADMIN sees reservations across all users")
    void testAdminSeesAllReservations() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("GET /api/reservations - Filter by status")
    void testFilterByStatus() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status", everyItem(is("CONFIRMED"))));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("GET /api/reservations - Filter by price range")
    void testFilterByPriceRange() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .param("minPrice", "100.00")
                        .param("maxPrice", "500.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].totalPrice", everyItem(greaterThanOrEqualTo(100.0))))
                .andExpect(jsonPath("$.content[*].totalPrice", everyItem(lessThanOrEqualTo(500.0))));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("GET /api/reservations - Pagination and sorting")
    void testPaginationAndSorting() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sortBy", "totalPrice")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.content", hasSize(lessThanOrEqualTo(2))));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("GET /api/reservations - Invalid sortBy field returns 400 Bad Request")
    void testInvalidSortByReturns400() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .param("sortBy", "invalidFieldAttack"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort field")));
    }

    @Test
    @WithUserDetails("user")
    @DisplayName("GET /api/reservations/{id} - USER can view own reservation")
    void testUserCanViewOwnReservation() throws Exception {
        Reservation ownReservation = reservationRepository.findByUserId(testUser.getId()).get(0);

        mockMvc.perform(get("/api/reservations/" + ownReservation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownReservation.getId()))
                .andExpect(jsonPath("$.username").value("user"));
    }

    @Test
    @WithUserDetails("user")
    @DisplayName("GET /api/reservations/{id} - USER cannot view another user's reservation (403 Forbidden)")
    void testUserCannotViewOtherReservation() throws Exception {
        Reservation otherReservation = reservationRepository.findByUserId(testOtherUser.getId()).get(0);

        mockMvc.perform(get("/api/reservations/" + otherReservation.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("user")
    @DisplayName("PATCH /api/reservations/{id}/status - USER can cancel their own reservation")
    void testUserCanCancelOwnReservation() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(25).withHour(10).withMinute(0);
        LocalDateTime end = LocalDateTime.now().plusDays(25).withHour(11).withMinute(0);

        Reservation res = reservationRepository.save(new Reservation(
                testUser, testResource, start, end, ReservationStatus.CONFIRMED, new BigDecimal("50.00"), "Notes"
        ));

        ReservationStatusUpdateRequest request = new ReservationStatusUpdateRequest(ReservationStatus.CANCELLED);

        mockMvc.perform(patch("/api/reservations/" + res.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithUserDetails("user")
    @DisplayName("PATCH /api/reservations/{id}/status - USER cannot change status to CONFIRMED (400 Bad Request)")
    void testUserCannotChangeToConfirmed() throws Exception {
        Reservation ownReservation = reservationRepository.findByUserId(testUser.getId()).get(0);
        ReservationStatusUpdateRequest request = new ReservationStatusUpdateRequest(ReservationStatus.CONFIRMED);

        mockMvc.perform(patch("/api/reservations/" + ownReservation.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("DELETE /api/reservations/{id} - ADMIN can delete reservation")
    void testAdminCanDeleteReservation() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0);
        LocalDateTime end = LocalDateTime.now().plusDays(30).withHour(11).withMinute(0);

        Reservation res = reservationRepository.save(new Reservation(
                testUser, testResource, start, end, ReservationStatus.CANCELLED, new BigDecimal("50.00"), "Notes"
        ));

        mockMvc.perform(delete("/api/reservations/" + res.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithUserDetails("user")
    @DisplayName("DELETE /api/reservations/{id} - USER cannot delete reservation (403 Forbidden)")
    void testUserCannotDeleteReservation() throws Exception {
        Reservation ownReservation = reservationRepository.findByUserId(testUser.getId()).get(0);

        mockMvc.perform(delete("/api/reservations/" + ownReservation.getId()))
                .andExpect(status().isForbidden());
    }
}
