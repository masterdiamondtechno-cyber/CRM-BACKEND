package com.company.crm.client.dto;

import com.company.crm.client.ClientStatus;
import java.util.UUID;

public record ClientView(
    UUID id,
    String companyName,
    String industry,
    String website,
    String email,
    String phone,
    String address,
    String notes,
    UUID ownerId,
    String ownerName,
    ClientStatus status) {}
