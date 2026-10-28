package com.company.crm.security;
import com.company.crm.user.User;import io.jsonwebtoken.Jwts;import io.jsonwebtoken.security.Keys;import javax.crypto.SecretKey;import java.util.*;import java.time.Instant;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;
@Service public class JwtService {
 private final SecretKey key;private final long minutes;
 public JwtService(@Value("${crm.jwt.secret}")String secret,@Value("${crm.jwt.expiration-minutes}")long m){key=Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));minutes=m;}
 public String issue(User u){Instant n=Instant.now();return Jwts.builder().subject(u.getEmail()).issuedAt(Date.from(n)).expiration(Date.from(n.plusSeconds(minutes*60))).signWith(key).compact();}
 public String subject(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();}public long expiresSeconds(){return minutes*60;}
}
