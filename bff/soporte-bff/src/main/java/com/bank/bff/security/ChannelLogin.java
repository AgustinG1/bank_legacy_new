package com.bank.bff.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChannelLogin {
    public record User(String channel, String username, String passwordHash, String accountKey) {}
    public record Pin(String accountKey, String pinHash) {}
    public record Terminal(String id, String keyHash) {}
    public record Credentials(List<User> users, List<Pin> pins, List<Terminal> terminals) {}
    public record Token(String accessToken, String tokenType, long expiresIn) {}
    private final Credentials credentials;
    private final IdentityRegistry identities;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final Map<String, Attempt> attempts = new HashMap<>();
    private record Attempt(int failures, long until) {}
    private final String dummyHash = passwords.encode(UUID.randomUUID().toString());

    public ChannelLogin(IdentityRegistry identities, ObjectMapper mapper,
            @Value("${bank.credentials-file:}") String file,
            @Value("${bank.web.username:web-demo}") String webUsername,
            @Value("${bank.web.password:WebDemo2026!}") String webPassword,
            @Value("${bank.mobile.username:mobile-demo}") String mobileUsername,
            @Value("${bank.mobile.password:MobileDemo2026!}") String mobilePassword,
            @Value("${bank.atm.pin:1234}") String atmPin,
            @Value("${bank.atm.terminal:ATM-001}") String terminal,
            @Value("${bank.atm.terminal-key:AtmTerminal2026!}") String terminalKey) throws java.io.IOException {
        this.identities = identities;
        this.credentials = file.isBlank() ? defaults(identities, webUsername, webPassword,
                mobileUsername, mobilePassword, atmPin, terminal, terminalKey)
                : mapper.readValue(Path.of(file).toFile(), Credentials.class);
        Objects.requireNonNull(credentials.users());
        Objects.requireNonNull(credentials.pins());
        Objects.requireNonNull(credentials.terminals());
    }

    private Credentials defaults(IdentityRegistry registry, String webUsername, String webPassword,
            String mobileUsername, String mobilePassword, String atmPin, String terminal, String terminalKey) {
        var web = registry.firstForRole("WEB").orElseThrow();
        var mobile = registry.firstForRole("MOBILE").orElseThrow();
        var atm = registry.firstForRole("ATM").orElseThrow();
        return new Credentials(
                List.of(new User("WEB", webUsername, passwords.encode(webPassword), web.accountKey()),
                        new User("MOBILE", mobileUsername, passwords.encode(mobilePassword), mobile.accountKey())),
                List.of(new Pin(atm.accountKey(), passwords.encode(atmPin))),
                List.of(new Terminal(terminal, hash(terminalKey))));
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public synchronized Token login(String channel, String username, String password) {
        User user = credentials.users().stream().filter(u -> channel.equals(u.channel())
                && u.username().equals(username)).findFirst().orElse(null);
        String bucket = channel + ":" + (user == null ? "unknown" : user.username());
        check(bucket);
        boolean matches = passwords.matches(password, user == null ? dummyHash : user.passwordHash());
        if (user == null || !matches) reject(bucket);
        attempts.remove(bucket);
        return token(channel, user.accountKey(), null, channel.equals("WEB") ? 900 : 300);
    }

    public synchronized Token atm(String accountKey, String pin, String terminal, String key) {
        Pin account = credentials.pins().stream().filter(p -> p.accountKey().equals(accountKey))
                .findFirst().orElse(null);
        String bucket = "ATM:" + (account == null ? "unknown" : account.accountKey());
        check(bucket);
        boolean validTerminal = terminalValid(terminal, key);
        boolean validPin = passwords.matches(pin, account == null ? dummyHash : account.pinHash());
        if (account == null || !validTerminal || !validPin) reject(bucket);
        attempts.remove(bucket);
        return token("ATM", accountKey, terminal, 120);
    }

    public boolean terminalValid(String terminal, String key) {
        if (terminal == null || key == null || key.length() > 256) return false;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return credentials.terminals().stream().anyMatch(t -> t.id().equals(terminal)
                    && MessageDigest.isEqual(HexFormat.of().parseHex(t.keyHash()), digest));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Token token(String channel, String accountKey, String terminal, long seconds) {
        return new Token(identities.issue(channel, accountKey, terminal, Duration.ofSeconds(seconds)),
                "Bearer", seconds);
    }

    private void check(String bucket) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> e.getValue().until() <= now);
        Attempt attempt = attempts.get(bucket);
        if (attempt != null && attempt.failures() >= 5)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Espere un minuto antes de reintentar");
    }

    private void reject(String bucket) {
        Attempt before = attempts.get(bucket);
        attempts.put(bucket, new Attempt(before == null ? 1 : before.failures() + 1,
                before == null ? System.currentTimeMillis() + 60_000 : before.until()));
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
    }
}
