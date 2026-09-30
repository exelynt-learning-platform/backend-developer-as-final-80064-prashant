package com.exelynt.booking.dto;

import com.exelynt.booking.entity.ResourceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class ResourceRequest {

    @NotBlank(message = "Resource name is required")
    private String name;

    @NotNull(message = "Resource type is required (ROOM, VEHICLE, EQUIPMENT, OTHER)")
    private ResourceType type;

    private String description;

    private String location;

    @Positive(message = "Capacity must be greater than zero")
    private Integer capacity;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Base price cannot be negative")
    private BigDecimal basePrice;

    private Boolean available = true;

    public ResourceRequest() {
    }

    public ResourceRequest(String name, ResourceType type, String description, String location, Integer capacity, BigDecimal basePrice, Boolean available) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.location = location;
        this.capacity = capacity;
        this.basePrice = basePrice;
        this.available = available != null ? available : true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ResourceType getType() {
        return type;
    }

    public void setType(ResourceType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }
}
