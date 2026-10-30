package com.company.crm.lead.service;

import com.company.crm.lead.LeadStatus;
import com.company.crm.lead.dto.*;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

public interface LeadService {

  Page<LeadView> listLeads(
      LeadStatus status, String search, int page, int size, Authentication auth);

  LeadView createLead(LeadRequest request, Authentication auth);

  LeadView getLead(UUID id, Authentication auth);

  LeadView updateLead(UUID id, LeadRequest request, Authentication auth);

  LeadView updateLeadStatus(UUID id, LeadStatusRequest request, Authentication auth);

  void archiveLead(UUID id, Authentication auth);

  ConversionResult convertLead(UUID id, ConversionRequest request, Authentication auth);
}
