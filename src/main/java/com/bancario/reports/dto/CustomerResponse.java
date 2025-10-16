package com.bancario.reports.dto;

import com.bancario.reports.enums.CustomerType;

public record CustomerResponse(
        String id,
        CustomerType type,
        String email,
        String phone,
        String firstName,
        String lastName,
        String dni,
        String businessName,
        String ruc,
        String legalRepresentative
) {}
