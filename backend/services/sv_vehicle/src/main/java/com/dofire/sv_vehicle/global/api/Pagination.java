package com.dofire.sv_vehicle.global.api;

public record Pagination(
    int page,
    int size,
    long totalItems,
    long totalPages
) {}
