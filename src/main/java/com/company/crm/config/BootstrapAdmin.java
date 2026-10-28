package com.company.crm.config;
import com.company.crm.user.*;import com.company.crm.security.TenantMembershipService;import org.springframework.beans.factory.annotation.Value;import org.springframework.boot.CommandLineRunner;import org.springframework.context.annotation.Bean;import org.springframework.context.annotation.Configuration;import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration public class BootstrapAdmin {
 @Bean CommandLineRunner bootstrap(UserRepository users,PasswordEncoder enc,TenantMembershipService memberships,@Value("${crm.bootstrap.admin-email}")String email,@Value("${crm.bootstrap.admin-password}")String password){return args->{if(email.isBlank()&&password.isBlank())return;if(email.isBlank()||password.length()<12)throw new IllegalStateException("Set ADMIN_EMAIL and ADMIN_PASSWORD (min 12 chars)");User admin=users.findByEmailIgnoreCase(email).orElseGet(()->users.save(new User("CRM Administrator",email,enc.encode(password),Role.ADMIN)));memberships.addBootstrapMembership(admin.getId());};}
}

