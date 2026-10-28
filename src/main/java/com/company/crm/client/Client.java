package com.company.crm.client;
import com.company.crm.user.User;import com.company.crm.security.TenantContext;import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="clients") public class Client {
 @Id @GeneratedValue(strategy=GenerationType.UUID)private UUID id;
 @Column(name="tenant_id",nullable=false,updatable=false)private UUID tenantId;
 @Column(name="company_name",nullable=false,length=200)private String companyName;
 @Column(length=100)private String industry;@Column(length=255)private String website;@Column(length=255)private String email;@Column(length=30)private String phone;@Column(columnDefinition="text")private String address;@Column(columnDefinition="text")private String notes;
 @ManyToOne(fetch=FetchType.LAZY)@JoinColumn(name="owner_id",nullable=false)private User owner;
 @Enumerated(EnumType.STRING)@Column(nullable=false,length=20)private ClientStatus status=ClientStatus.PROSPECT;
 @Column(name="archived_at")private Instant archivedAt;@Column(name="created_at",nullable=false,updatable=false)private Instant createdAt=Instant.now();@Column(name="updated_at",nullable=false)private Instant updatedAt=Instant.now();
 protected Client(){}public Client(String n,String i,String w,String e,String p,String a,String notes,User o,ClientStatus s){companyName=n;industry=i;website=w;email=e;phone=p;address=a;this.notes=notes;owner=o;if(s!=null)status=s;}
 @PrePersist void attachTenant(){tenantId=TenantContext.requireTenantId();}
 @PreUpdate void touch(){updatedAt=Instant.now();}
 public void archive(){archivedAt=Instant.now();}public UUID getId(){return id;}public String getCompanyName(){return companyName;}public String getIndustry(){return industry;}public String getWebsite(){return website;}public String getEmail(){return email;}public String getPhone(){return phone;}public String getAddress(){return address;}public String getNotes(){return notes;}public User getOwner(){return owner;}public ClientStatus getStatus(){return status;}
 public void update(String n,String i,String w,String e,String p,String a,String notes,ClientStatus s,User o){companyName=n;industry=i;website=w;email=e;phone=p;address=a;this.notes=notes;if(s!=null)status=s;if(o!=null)owner=o;}
}


