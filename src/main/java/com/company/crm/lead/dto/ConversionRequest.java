package com.company.crm.lead.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversionRequest(
    @NotBlank @Size(max = 200) String companyName,
    @Size(max = 100) String industry,
    @Size(max = 255) String website,
    @Email @Size(max = 255) String email,
    @Size(max = 30) String phone,
    String address,
    String notes,
    @Size(max = 120) String contactFullName,
    @Size(max = 120) String contactJobTitle,
    @Email @Size(max = 255) String contactEmail,
    @Size(max = 30) String contactPhone,
    String contactNotes) {}
