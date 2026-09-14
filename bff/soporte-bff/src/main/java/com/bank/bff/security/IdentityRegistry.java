package com.bank.bff.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

@Component
public class IdentityRegistry {
    public record Identity(String role, String token, String accountKey) {}

    private final List<Identity> identities;
    private final boolean allowLegacyTokens;
    private final java.util.Map<Identity, NimbusJwtDecoder> decoders = new java.util.HashMap<>();
    public record Verified(String role, String accountKey, String terminal) {}

    public IdentityRegistry(
            @Value("${bank.web-identities}") String web,
            @Value("${bank.mobile-identities}") String mobile,
            @Value("${bank.atm-identities}") String atm,
            @Value("${bank.security.allow-legacy-tokens:false}") boolean allowLegacyTokens) {
        this.allowLegacyTokens = allowLegacyTokens;
        var configured = new ArrayList<Identity>();
        configured.addAll(parse("WEB", web));
        configured.addAll(parse("MOBILE", mobile));
        configured.addAll(parse("ATM", atm));

        var tokens = new HashSet<String>();
        for (Identity identity : configured) {
            if (!tokens.add(identity.token())) {
                throw new IllegalArgumentException("Cada identidad debe utilizar un token distinto");
            }
        }
        identities = List.copyOf(configured);
        for (Identity identity : identities) {
            var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(
                    identity.token().getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(Duration.ZERO),
                    new JwtIssuerValidator("bank-xyz/" + identity.role())));
            decoders.put(identity, decoder);
        }
    }

    public Optional<Verified> authenticate(String suppliedToken) {
        if (suppliedToken.length() > 4096) return Optional.empty();
        if (allowLegacyTokens) {
            byte[] supplied = suppliedToken.getBytes(StandardCharsets.UTF_8);
            for (Identity identity : identities) {
                if (MessageDigest.isEqual(supplied, identity.token().getBytes(StandardCharsets.UTF_8))) {
                    return Optional.of(new Verified(identity.role(), identity.accountKey(), null));
                }
            }
        }
        for (Identity identity : identities) {
            try {
                Jwt jwt = decoders.get(identity).decode(suppliedToken);
                if (jwt.getExpiresAt() != null && jwt.getAudience().contains("bank-xyz")
                        && identity.accountKey().equals(jwt.getSubject())) {
                    return Optional.of(new Verified(identity.role(), identity.accountKey(),
                            jwt.getClaimAsString("terminal")));
                }
            } catch (JwtException | IllegalArgumentException ignored) {
            }
        }
        return Optional.empty();
    }

    public String issue(String role, String accountKey, String terminal, Duration lifetime) {
        Identity identity = identities.stream().filter(i -> i.role().equals(role)
                && i.accountKey().equals(accountKey)).findFirst().orElseThrow();
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("bank-xyz/" + role)
                .subject(accountKey).audience(List.of("bank-xyz")).issuedAt(now)
                .expiresAt(now.plus(lifetime)).id(UUID.randomUUID().toString());
        if (terminal != null) claims.claim("terminal", terminal);
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                identity.token().getBytes(StandardCharsets.UTF_8)));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    public Set<String> accountKeys() {
        var keys = new HashSet<String>();
        identities.forEach(identity -> keys.add(identity.accountKey()));
        return Set.copyOf(keys);
    }

    public Optional<Identity> firstForRole(String role) {
        return identities.stream().filter(identity -> identity.role().equals(role)).findFirst();
    }

    private List<Identity> parse(String role, String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("Debe configurar identidades para el canal " + role);
        }
        var result = new ArrayList<Identity>();
        for (String entry : configured.split(",")) {
            int separator = entry.lastIndexOf('=');
            if (separator < 32 || separator == entry.length() - 1) {
                throw new IllegalArgumentException(
                        "Formato de identidad invalido para el canal " + role);
            }
            String token = entry.substring(0, separator).trim();
            String accountKey = entry.substring(separator + 1).trim();
            if (token.length() < 32 || accountKey.isBlank() || accountKey.length() > 36) {
                throw new IllegalArgumentException(
                        "Token o cuenta invalida para el canal " + role);
            }
            result.add(new Identity(role, token, accountKey));
        }
        return result;
    }
}
