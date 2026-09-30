package com.exelynt.booking.dto;

import com.exelynt.booking.entity.Resource;
import com.exelynt.booking.entity.ResourceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ResourceResponse {

    private Long id;
    private String name;
    private ResourceType type;
    private String description;
    private String location;
    private Integer capacity;
    private BigDecimal basePrice;
    private boolean available;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ResourceResponse() {
    }

    public ResourceResponse(Long id, String name, ResourceType type, String description, String location, Integer capacity, BigDecimal basePrice, boolean available, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.location = location;
        this.capacity = capacity;
        this.basePrice = basePrice;
        this.available = available;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ResourceResponse fromEntity(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getType(),
                resource.getDescription(),
                resource.getLocation(),
                resource.getCapacity(),
                resource.getBasePrice(),
                resource.isAvailable(),
                resource.getCreatedAt(),
                resource.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
