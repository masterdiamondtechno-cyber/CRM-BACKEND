package com.company.crm.lead.dto;

import java.util.UUID;

public record ConversionResult(UUID leadId, UUID clientId, UUID contactId) {}
