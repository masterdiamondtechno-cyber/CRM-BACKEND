package com.company.crm.client.service;

import com.company.crm.client.Client;
import com.company.crm.client.ClientStatus;
import com.company.crm.client.dto.ClientRequest;
import com.company.crm.client.dto.ClientView;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

public interface ClientService {

  Page<ClientView> listClients(
      String search,
      ClientStatus status,
      UUID ownerId,
      LocalDate createdFrom,
      LocalDate createdTo,
      int page,
      int size,
      String sortBy,
      String sortDir,
      Authentication auth);

  ClientView createClient(ClientRequest request, Authentication auth);

  Map<String, Object> getClientDetail(UUID id, Authentication auth);

  ClientView updateClient(UUID id, ClientRequest request, Authentication auth);

  void deleteClient(UUID id, Authentication auth);

  Client findVisibleClient(UUID id, Authentication auth);
}
