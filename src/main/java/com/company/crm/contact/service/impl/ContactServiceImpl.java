package com.company.crm.contact.service.impl;

import com.company.crm.client.Client;
import com.company.crm.client.ClientRepository;
import com.company.crm.contact.Contact;
import com.company.crm.contact.ContactRepository;
import com.company.crm.contact.dto.ContactRequest;
import com.company.crm.contact.dto.ContactView;
import com.company.crm.contact.service.ContactService;
import com.company.crm.security.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ContactServiceImpl implements ContactService {

  private final ContactRepository contacts;
  private final ClientRepository clients;

  public ContactServiceImpl(ContactRepository contacts, ClientRepository clients) {
    this.contacts = contacts;
    this.clients = clients;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ContactView> searchContacts(String search, int page, int size, Authentication auth) {
    return contacts
        .search(
            TenantContext.requireTenantId(),
            isManagerOrAdmin(auth),
            auth.getName(),
            search == null ? "" : search,
            PageRequest.of(page, size, Sort.by("fullName")))
        .map(this::toView);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ContactView> listContactsByClient(
      UUID clientId, int page, int size, Authentication auth) {
    UUID tenantId = TenantContext.requireTenantId();
    clients
        .findVisible(tenantId, clientId, isManagerOrAdmin(auth), auth.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

    return contacts
        .byClient(
            tenantId,
            clientId,
            isManagerOrAdmin(auth),
            auth.getName(),
            PageRequest.of(page, size, Sort.by("fullName")))
        .map(this::toView);
  }

  @Override
  public ContactView createContact(UUID clientId, ContactRequest r, Authentication auth) {
    Client client =
        clients
            .findVisible(
                TenantContext.requireTenantId(), clientId, isManagerOrAdmin(auth), auth.getName())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

    Contact contact =
        new Contact(
            client, r.fullName(), r.jobTitle(), r.email(), r.phone(), r.primary(), r.notes());
    return toView(contacts.save(contact));
  }

  @Override
  @Transactional(readOnly = true)
  public ContactView getContact(UUID id, Authentication auth) {
    return toView(findVisibleContact(id, auth));
  }

  @Override
  public ContactView updateContact(UUID id, ContactRequest r, Authentication auth) {
    Contact contact = findVisibleContact(id, auth);
    contact.set(
        contact.getClient(),
        r.fullName(),
        r.jobTitle(),
        r.email(),
        r.phone(),
        r.primary(),
        r.notes());
    return toView(contacts.save(contact));
  }

  @Override
  public void archiveContact(UUID id, Authentication auth) {
    Contact contact = findVisibleContact(id, auth);
    contact.archive();
    contacts.save(contact);
  }

  private Contact findVisibleContact(UUID id, Authentication auth) {
    return contacts
        .visible(TenantContext.requireTenantId(), id, isManagerOrAdmin(auth), auth.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found"));
  }

  private ContactView toView(Contact c) {
    return new ContactView(
        c.getId(),
        c.getClient().getId(),
        c.getFullName(),
        c.getJobTitle(),
        c.getEmail(),
        c.getPhone(),
        c.isPrimary(),
        c.getNotes());
  }

  private boolean isManagerOrAdmin(Authentication a) {
    return a.getAuthorities().stream()
        .anyMatch(
            x -> x.getAuthority().equals("ROLE_ADMIN") || x.getAuthority().equals("ROLE_MANAGER"));
  }
}
