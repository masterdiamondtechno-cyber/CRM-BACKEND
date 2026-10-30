package com.company.crm.client.dto;

import com.company.crm.client.ClientStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ClientRequest(
    @NotBlank @Size(max = 200) String companyName,
    @Size(max = 100) String industry,
    @Size(max = 255) String website,
    @Email @Size(max = 255) String email,
    @Size(max = 30) String phone,
    String address,
    String notes,
    UUID ownerId,
    ClientStatus status) {}
