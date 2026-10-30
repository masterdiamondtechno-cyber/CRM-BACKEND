package com.company.crm.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContactRequest(
    @NotBlank @Size(max = 120) String fullName,
    @Size(max = 120) String jobTitle,
    @Email @Size(max = 255) String email,
    @Pattern(regexp = "^[+0-9() .-]{0,30}$") String phone,
    boolean primary,
    String notes) {}
