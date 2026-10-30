package com.company.crm.contact.dto;

import java.util.UUID;

public record ContactView(
    UUID id,
    UUID clientId,
    String fullName,
    String jobTitle,
    String email,
    String phone,
    boolean primary,
    String notes) {}
