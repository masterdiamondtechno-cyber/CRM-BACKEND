package com.company.crm.contact.service;

import com.company.crm.contact.dto.ContactRequest;
import com.company.crm.contact.dto.ContactView;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

public interface ContactService {

  Page<ContactView> searchContacts(String search, int page, int size, Authentication auth);

  Page<ContactView> listContactsByClient(UUID clientId, int page, int size, Authentication auth);

  ContactView createContact(UUID clientId, ContactRequest request, Authentication auth);

  ContactView getContact(UUID id, Authentication auth);

  ContactView updateContact(UUID id, ContactRequest request, Authentication auth);

  void archiveContact(UUID id, Authentication auth);
}
