package com.company.crm.client;

import com.company.crm.client.dto.ClientRequest;
import com.company.crm.client.dto.ClientView;
import com.company.crm.client.service.ClientService;
import com.company.crm.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clients")
@Validated
public class ClientController {

  private final ClientService clientService;

  public ClientController(ClientService clientService) {
    this.clientService = clientService;
  }

  @GetMapping
  public ApiResponse<Page<ClientView>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) ClientStatus status,
      @RequestParam(required = false) UUID ownerId,
      @RequestParam(required = false) LocalDate createdFrom,
      @RequestParam(required = false) LocalDate createdTo,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "createdAt") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDir,
      Authentication auth) {
    return ApiResponse.ok(
        "Clients",
        clientService.listClients(
            search, status, ownerId, createdFrom, createdTo, page, size, sortBy, sortDir, auth));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ClientView>> create(
      @Valid @RequestBody ClientRequest request, Authentication auth) {
    return ResponseEntity.status(201)
        .body(ApiResponse.ok("Client created", clientService.createClient(request, auth)));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable UUID id, Authentication auth) {
    return ApiResponse.ok("Client detail", clientService.getClientDetail(id, auth));
  }

  @PatchMapping("/{id}")
  public ApiResponse<ClientView> update(
      @PathVariable UUID id, @Valid @RequestBody ClientRequest request, Authentication auth) {
    return ApiResponse.ok("Client updated", clientService.updateClient(id, request, auth));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
    clientService.deleteClient(id, auth);
    return ResponseEntity.noContent().build();
  }
}
