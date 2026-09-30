package com.exelynt.booking.dto;

import com.exelynt.booking.entity.ReservationStatus;
import io.swagger.v3.oas.annotations.Parameter;

import java.math.BigDecimal;

public class ReservationQueryFilter {

    @Parameter(description = "Filter by reservation status (PENDING, CONFIRMED, CANCELLED)")
    private ReservationStatus status;

    @Parameter(description = "Filter by minimum price (decimal)")
    private BigDecimal minPrice;

    @Parameter(description = "Filter by maximum price (decimal)")
    private BigDecimal maxPrice;

    @Parameter(description = "Page number (0-indexed, default: 0)")
    private int page = 0;

    @Parameter(description = "Number of items per page (default: 10)")
    private int size = 10;

    @Parameter(description = "Field to sort by (e.g., createdAt, totalPrice, startTime)")
    private String sortBy = "createdAt";

    @Parameter(description = "Sort direction (asc or desc, default: desc)")
    private String sortDir = "desc";

    public ReservationQueryFilter() {
    }

    public ReservationQueryFilter(ReservationStatus status, BigDecimal minPrice, BigDecimal maxPrice, int page, int size, String sortBy, String sortDir) {
        this.status = status;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.page = page;
        this.size = size;
        this.sortBy = sortBy != null ? sortBy : "createdAt";
        this.sortDir = sortDir != null ? sortDir : "desc";
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDir() {
        return sortDir;
    }

    public void setSortDir(String sortDir) {
        this.sortDir = sortDir;
    }
}
