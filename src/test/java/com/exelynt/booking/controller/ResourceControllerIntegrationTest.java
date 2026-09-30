package com.exelynt.booking.controller;

import com.exelynt.booking.dto.ResourceRequest;
import com.exelynt.booking.entity.Resource;
import com.exelynt.booking.entity.ResourceType;
import com.exelynt.booking.repository.ResourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ResourceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResourceRepository resourceRepository;

    @Test
    @DisplayName("GET /api/resources - Unauthenticated request returns 401")
    void testUnauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("GET /api/resources - USER role can read resources")
    void testUserCanReadResources() throws Exception {
        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("POST /api/resources - USER role cannot create resource (403 Forbidden)")
    void testUserCannotCreateResource() throws Exception {
        ResourceRequest request = new ResourceRequest(
                "Unauthorized Room",
                ResourceType.ROOM,
                "Desc",
                "Loc",
                10,
                new BigDecimal("50.00"),
                true
        );

        mockMvc.perform(post("/api/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("PUT /api/resources/{id} - USER role cannot update resource (403 Forbidden)")
    void testUserCannotUpdateResource() throws Exception {
        Resource resource = resourceRepository.findAll().get(0);
        ResourceRequest request = new ResourceRequest(
                "Modified Name",
                resource.getType(),
                "Desc",
                "Loc",
                5,
                new BigDecimal("40.00"),
                true
        );

        mockMvc.perform(put("/api/resources/" + resource.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("DELETE /api/resources/{id} - USER role cannot delete resource (403 Forbidden)")
    void testUserCannotDeleteResource() throws Exception {
        Resource resource = resourceRepository.findAll().get(0);

        mockMvc.perform(delete("/api/resources/" + resource.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("POST /api/resources - ADMIN role can create resource")
    void testAdminCanCreateResource() throws Exception {
        ResourceRequest request = new ResourceRequest(
                "Innovation Lab",
                ResourceType.ROOM,
                "High tech workspace with 3D printers and VR rigs",
                "Level 5, Suite 500",
                20,
                new BigDecimal("95.00"),
                true
        );

        mockMvc.perform(post("/api/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Innovation Lab"))
                .andExpect(jsonPath("$.basePrice").value(95.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("PUT /api/resources/{id} - ADMIN role can update resource")
    void testAdminCanUpdateResource() throws Exception {
        Resource resource = resourceRepository.save(new Resource(
                "Original Lab", ResourceType.ROOM, "desc", "loc", 10, new BigDecimal("45.00"), true
        ));

        ResourceRequest updateRequest = new ResourceRequest(
                "Updated Lab Name",
                ResourceType.ROOM,
                "updated desc",
                "new loc",
                15,
                new BigDecimal("55.00"),
                true
        );

        mockMvc.perform(put("/api/resources/" + resource.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Lab Name"))
                .andExpect(jsonPath("$.basePrice").value(55.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("DELETE /api/resources/{id} - ADMIN role can delete resource")
    void testAdminCanDeleteResource() throws Exception {
        Resource resource = resourceRepository.save(new Resource(
                "Temporary Resource", ResourceType.EQUIPMENT, "to be deleted", "loc", 1, new BigDecimal("10.00"), true
        ));

        mockMvc.perform(delete("/api/resources/" + resource.getId()))
                .andExpect(status().isNoContent());
    }
}
