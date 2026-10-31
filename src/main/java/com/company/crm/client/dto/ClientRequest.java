package com.company.crm.client.dto;

import com.company.crm.client.ClientStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ClientRequest(
    @NotBlank(message = "Company name is required")
        @Size(min = 2, max = 200, message = "Company name must be between 2 and 200 characters")
        String companyName,
    @Size(max = 100, message = "Industry cannot exceed 100 characters")
        String industry,
    @Pattern(
            regexp = "^$|^(https?://)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$",
            message = "Invalid website URL format (must be valid domain or URL)")
        @Size(max = 255, message = "Website cannot exceed 255 characters")
        String website,
    @Pattern(
            regexp = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$",
            message = "Invalid email format")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,
    @Pattern(
            regexp = "^$|^[+]?[0-9\\s\\-().]{7,30}$",
            message = "Invalid phone number format (must contain 7-30 digits, optional '+' and dashes)")
        @Size(max = 30, message = "Phone cannot exceed 30 characters")
        String phone,
    @Size(max = 1000, message = "Address cannot exceed 1000 characters")
        String address,
    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
        String notes,
    UUID ownerId,
    ClientStatus status) {

  public ClientRequest {
    companyName = companyName != null ? companyName.trim() : null;
    industry = clean(industry);
    website = clean(website);
    email = clean(email);
    phone = clean(phone);
    address = clean(address);
    notes = clean(notes);
  }

  private static String clean(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}
