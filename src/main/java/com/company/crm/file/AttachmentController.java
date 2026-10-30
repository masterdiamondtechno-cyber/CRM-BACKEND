package com.company.crm.file;

import com.company.crm.audit.AuditService;
import com.company.crm.client.ClientRepository;
import com.company.crm.common.ApiResponse;
import com.company.crm.lead.LeadRepository;
import com.company.crm.security.TenantContext;
import com.company.crm.user.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class AttachmentController {
  private static final Logger log = LoggerFactory.getLogger(AttachmentController.class);
  private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
  private final JdbcTemplate db;
  private final ClientRepository clients;
  private final LeadRepository leads;
  private final UserRepository users;
  private final AuditService audit;
  private final Path storageRoot;

  public AttachmentController(
      JdbcTemplate db,
      ClientRepository clients,
      LeadRepository leads,
      UserRepository users,
      AuditService audit,
      @Value("${crm.files.directory:./data/files}") String storageDirectory) {
    this.db = db;
    this.clients = clients;
    this.leads = leads;
    this.users = users;
    this.audit = audit;
    this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
  }

  public record AttachmentView(
      UUID id,
      String fileName,
      String contentType,
      long sizeBytes,
      Instant createdAt,
      UUID uploadedBy) {}

  @GetMapping({
    "/clients/{recordId}/attachments",
    "/leads/{recordId}/attachments",
    "/projects/{recordId}/attachments"
  })
  public ApiResponse<List<AttachmentView>> list(
      @PathVariable UUID recordId,
      Authentication authentication,
      jakarta.servlet.http.HttpServletRequest request) {
    Parent parent = parentFromRequest(request);
    requireVisible(parent, recordId, authentication);
    String sql =
        """
        select id, original_filename, content_type, size_bytes, created_at, uploaded_by
        from crm_attachments where tenant_id = ? and %s = ? order by created_at desc, id
        """
            .formatted(parent.column);
    return ApiResponse.ok(
        "Attachments",
        db.query(
            sql,
            (rs, row) ->
                new AttachmentView(
                    rs.getObject("id", UUID.class),
                    rs.getString("original_filename"),
                    rs.getString("content_type"),
                    rs.getLong("size_bytes"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getObject("uploaded_by", UUID.class)),
            TenantContext.requireTenantId(),
            recordId));
  }

  @PostMapping({
    "/clients/{recordId}/attachments",
    "/leads/{recordId}/attachments",
    "/projects/{recordId}/attachments"
  })
  @Transactional
  public ResponseEntity<ApiResponse<AttachmentView>> upload(
      @PathVariable UUID recordId,
      @RequestPart("file") MultipartFile file,
      Authentication authentication,
      jakarta.servlet.http.HttpServletRequest request) {
    Parent parent = parentFromRequest(request);
    requireVisible(parent, recordId, authentication);
    if (file.isEmpty())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty file");
    if (file.getSize() > MAX_FILE_BYTES)
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 10 MB limit");

    UUID tenant = TenantContext.requireTenantId();
    UUID actor = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow().getId();
    UUID attachmentId = UUID.randomUUID();
    String storageKey = attachmentId + ".bin";
    Path target = safePath(storageKey);
    String filename = safeFilename(file.getOriginalFilename());
    String contentType = safeContentType(file.getContentType());
    try {
      Files.createDirectories(storageRoot);
      try (var input = file.getInputStream()) {
        Files.copy(input, target);
      }
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED) {
                try {
                  Files.deleteIfExists(target);
                } catch (IOException cleanupFailure) {
                  log.error(
                      "Could not remove uncommitted attachment {}", attachmentId, cleanupFailure);
                }
              }
            }
          });
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target));
      UUID clientId = parent.kind.equals("CLIENT") ? recordId : null;
      UUID leadId = parent.kind.equals("LEAD") ? recordId : null;
      UUID projectId = parent.kind.equals("PROJECT") ? recordId : null;
      db.update(
          """
          insert into crm_attachments(id, tenant_id, client_id, lead_id, project_id, uploaded_by,
              original_filename, content_type, size_bytes, sha256, storage_key)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          attachmentId,
          tenant,
          clientId,
          leadId,
          projectId,
          actor,
          filename,
          contentType,
          file.getSize(),
          HexFormat.of().formatHex(digest),
          storageKey);
      audit.record(authentication, "ATTACHMENT_UPLOADED", "ATTACHMENT", attachmentId);
      Instant createdAt =
          db.queryForObject(
              "select created_at from crm_attachments where tenant_id=? and id=?",
              (rs, row) -> rs.getTimestamp(1).toInstant(),
              tenant,
              attachmentId);
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(
              ApiResponse.ok(
                  "Attachment uploaded",
                  new AttachmentView(
                      attachmentId, filename, contentType, file.getSize(), createdAt, actor)));
    } catch (IOException | NoSuchAlgorithmException | RuntimeException failure) {
      try {
        Files.deleteIfExists(target);
      } catch (IOException ignored) {
      }
      if (failure instanceof RuntimeException runtime) throw runtime;
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Could not store attachment");
    }
  }

  @GetMapping("/attachments/{attachmentId}/download")
  public ResponseEntity<ByteArrayResource> download(
      @PathVariable UUID attachmentId, Authentication authentication) {
    Map<String, Object> metadata = metadata(attachmentId);
    requireVisibleAttachment(metadata, authentication);
    Path path = safePath((String) metadata.get("storage_key"));
    try {
      byte[] content = Files.readAllBytes(path);
      String filename = (String) metadata.get("original_filename");
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .contentLength(content.length)
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              ContentDisposition.attachment()
                  .filename(filename, StandardCharsets.UTF_8)
                  .build()
                  .toString())
          .header(HttpHeaders.CACHE_CONTROL, "no-store")
          .header("X-Content-Type-Options", "nosniff")
          .body(new ByteArrayResource(content));
    } catch (IOException failure) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment content not found");
    }
  }

  @DeleteMapping("/attachments/{attachmentId}")
  @Transactional
  public ResponseEntity<Void> delete(
      @PathVariable UUID attachmentId, Authentication authentication) {
    Map<String, Object> metadata = metadata(attachmentId);
    requireVisibleAttachment(metadata, authentication);
    UUID uploader = (UUID) metadata.get("uploaded_by");
    UUID actor = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow().getId();
    if (!broad(authentication) && !uploader.equals(actor)) {
      throw new AccessDeniedException(
          "Only the uploader or a workspace manager can remove an attachment");
    }
    Path storagePath = safePath((String) metadata.get("storage_key"));
    db.update(
        "delete from crm_attachments where tenant_id=? and id=?",
        TenantContext.requireTenantId(),
        attachmentId);
    audit.record(authentication, "ATTACHMENT_DELETED", "ATTACHMENT", attachmentId);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              Files.deleteIfExists(storagePath);
            } catch (IOException cleanupFailure) {
              log.error(
                  "Could not remove deleted attachment content {}", attachmentId, cleanupFailure);
            }
          }
        });
    return ResponseEntity.noContent().build();
  }

  private Map<String, Object> metadata(UUID id) {
    try {
      return db.queryForMap(
          "select tenant_id, client_id, lead_id, project_id, uploaded_by, original_filename,"
              + " storage_key from crm_attachments where tenant_id=? and id=?",
          TenantContext.requireTenantId(),
          id);
    } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found");
    }
  }

  private void requireVisibleAttachment(
      Map<String, Object> metadata, Authentication authentication) {
    UUID clientId = (UUID) metadata.get("client_id");
    UUID leadId = (UUID) metadata.get("lead_id");
    UUID projectId = (UUID) metadata.get("project_id");
    if (clientId != null)
      requireVisible(new Parent("CLIENT", "client_id"), clientId, authentication);
    else if (leadId != null) requireVisible(new Parent("LEAD", "lead_id"), leadId, authentication);
    else if (projectId != null)
      requireVisible(new Parent("PROJECT", "project_id"), projectId, authentication);
    else throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found");
  }

  private void requireVisible(Parent parent, UUID id, Authentication authentication) {
    UUID tenant = TenantContext.requireTenantId();
    boolean canSeeAll = broad(authentication);
    switch (parent.kind) {
      case "CLIENT" ->
          clients
              .findVisible(tenant, id, canSeeAll, authentication.getName())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
      case "LEAD" ->
          leads
              .visible(tenant, id, canSeeAll, authentication.getName())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
      case "PROJECT" -> {
        Integer count =
            db.queryForObject(
                """
select count(*) from projects p join users owner on owner.id=p.owner_id
where p.tenant_id=? and p.id=? and p.archived_at is null
  and (? or lower(owner.email)=lower(?) or exists (
    select 1 from project_members m join users member on member.id=m.user_id
    where m.tenant_id=p.tenant_id and m.project_id=p.id and lower(member.email)=lower(?)
  ))
""",
                Integer.class,
                tenant,
                id,
                canSeeAll,
                authentication.getName(),
                authentication.getName());
        if (count == null || count == 0)
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
      }
      default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
    }
  }

  private Parent parentFromRequest(jakarta.servlet.http.HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.contains("/clients/")) return new Parent("CLIENT", "client_id");
    if (path.contains("/leads/")) return new Parent("LEAD", "lead_id");
    if (path.contains("/projects/")) return new Parent("PROJECT", "project_id");
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment parent not found");
  }

  private boolean broad(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(
            authority ->
                authority.getAuthority().equals("ROLE_ADMIN")
                    || authority.getAuthority().equals("ROLE_MANAGER"));
  }

  private Path safePath(String storageKey) {
    Path path = storageRoot.resolve(storageKey).normalize();
    if (!path.startsWith(storageRoot) || storageKey.contains("/") || storageKey.contains("\\")) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment content not found");
    }
    return path;
  }

  private String safeFilename(String original) {
    if (original == null || original.isBlank()) return "attachment";
    String basename = original.replace('\\', '/');
    basename =
        basename.substring(basename.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
    if (basename.isBlank() || basename.equals(".") || basename.equals("..")) return "attachment";
    return basename.length() > 255 ? basename.substring(basename.length() - 255) : basename;
  }

  private String safeContentType(String contentType) {
    if (contentType == null || contentType.isBlank() || contentType.length() > 127)
      return "application/octet-stream";
    return contentType.replaceAll("[^A-Za-z0-9!#$&^_.+-/;= ]", "");
  }

  private record Parent(String kind, String column) {}
}
