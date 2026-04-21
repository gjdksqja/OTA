package com.syncml.server.syncml.service;

import com.syncml.server.domain.DeviceCredential;
import com.syncml.server.repository.DeviceCredentialRepository;
import com.syncml.server.syncml.dto.Cred;
import com.syncml.server.syncml.dto.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * SyncML 인증 서비스
 *
 * SyncML Cred 요소를 처리하고 인증 검증 수행.
 *
 * 지원 인증 타입:
 * 1. syncml:auth-basic - Base64(username:password)
 * 2. syncml:auth-md5   - Base64(MD5(username:password:nonce))
 * 3. syncml:auth-hmac  - HMAC 기반 (미구현)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncMLAuthService {

    private final DeviceCredentialRepository credentialRepository;

    /**
     * 인증 검증
     *
     * @param vin  단말 VIN
     * @param cred 클라이언트가 보낸 Cred
     * @return 인증 결과
     */
    @Transactional
    public AuthResult authenticate(String vin, Cred cred) {
        if (cred == null) {
            log.warn("No credentials provided for VIN: {}", vin);
            return AuthResult.requiresAuth(generateNonce());
        }

        Optional<DeviceCredential> credOpt = credentialRepository.findById(vin);
        if (credOpt.isEmpty()) {
            log.warn("No credential registered for VIN: {}", vin);
            return AuthResult.failed("Unknown device");
        }

        DeviceCredential deviceCred = credOpt.get();

        // 잠금 확인
        if (deviceCred.getLocked()) {
            log.warn("Device {} is locked due to too many failed attempts", vin);
            return AuthResult.failed("Account locked");
        }

        boolean valid = switch (cred.getType()) {
            case "syncml:auth-basic" -> validateBasicAuth(cred, deviceCred);
            case "syncml:auth-md5" -> validateMd5Auth(cred, deviceCred);
            default -> {
                log.warn("Unsupported auth type: {}", cred.getType());
                yield false;
            }
        };

        if (valid) {
            deviceCred.recordSuccess();
            credentialRepository.save(deviceCred);
            log.info("Authentication successful for VIN: {}", vin);
            return AuthResult.success();
        } else {
            deviceCred.recordFailure();
            credentialRepository.save(deviceCred);
            log.warn("Authentication failed for VIN: {}", vin);
            return AuthResult.failed("Invalid credentials");
        }
    }

    /**
     * Basic 인증 검증
     * Data = Base64(username:password)
     */
    private boolean validateBasicAuth(Cred cred, DeviceCredential deviceCred) {
        try {
            String decoded = new String(Base64.getDecoder().decode(cred.getData()), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            if (parts.length != 2) {
                return false;
            }
            String username = parts[0];
            String password = parts[1];

            // 비밀번호 해시 비교
            String passwordHash = hashPassword(password);
            return deviceCred.getUsername().equals(username)
                    && deviceCred.getPasswordHash().equals(passwordHash);
        } catch (Exception e) {
            log.error("Failed to decode basic auth", e);
            return false;
        }
    }

    /**
     * MD5 인증 검증
     * Data = Base64(MD5(username:password:nonce))
     */
    private boolean validateMd5Auth(Cred cred, DeviceCredential deviceCred) {
        try {
            // 클라이언트가 보낸 해시 (Base64 디코딩)
            byte[] clientHash = Base64.getDecoder().decode(cred.getData());

            // 서버 측 해시 계산
            String input = deviceCred.getUsername() + ":"
                    + getPasswordFromHash(deviceCred) + ":"
                    + deviceCred.getServerNonce();
            byte[] serverHash = md5(input);

            return MessageDigest.isEqual(clientHash, serverHash);
        } catch (Exception e) {
            log.error("Failed to validate MD5 auth", e);
            return false;
        }
    }

    /**
     * 새 nonce 생성 (MD5 인증용)
     */
    public String generateNonce() {
        SecureRandom random = new SecureRandom();
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }

    /**
     * 단말에 nonce 저장 (다음 인증 요청에서 사용)
     */
    @Transactional
    public void updateNonce(String vin, String nonce) {
        credentialRepository.findById(vin).ifPresent(cred -> {
            cred.updateNonce(nonce);
            credentialRepository.save(cred);
        });
    }

    /**
     * 인증 챌린지 생성 (401/407 응답용)
     */
    public Status.Chal createChallenge(String authType, String nonce) {
        return Status.Chal.builder()
                .type(authType)
                .format("b64")
                .nonce(nonce)
                .build();
    }

    /**
     * 비밀번호 해시 (저장용)
     */
    private String hashPassword(String password) {
        try {
            byte[] hash = md5(password);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    private byte[] md5(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        return md.digest(input.getBytes(StandardCharsets.UTF_8));
    }

    // 실제로는 해시에서 원본 비밀번호를 알 수 없음.
    // MD5 인증 시에는 별도 저장 또는 다른 방식 필요.
    // 여기서는 데모용으로 단순화.
    private String getPasswordFromHash(DeviceCredential cred) {
        // 실무에서는 MD5 인증용 비밀번호를 별도 저장하거나,
        // 클라이언트에서 username:password:nonce의 MD5를 직접 계산하도록 함
        return "demo_password"; // 데모용
    }

    /**
     * 신규 단말 인증 정보 등록
     */
    @Transactional
    public DeviceCredential registerCredential(String vin, String username, String password, String authType) {
        DeviceCredential cred = DeviceCredential.builder()
                .vin(vin)
                .username(username)
                .passwordHash(hashPassword(password))
                .authType(authType)
                .serverNonce(generateNonce())
                .build();
        return credentialRepository.save(cred);
    }

    /**
     * 인증 결과 클래스
     */
    public record AuthResult(
            boolean success,
            boolean requiresAuth,
            String nonce,
            String errorMessage
    ) {
        public static AuthResult success() {
            return new AuthResult(true, false, null, null);
        }

        public static AuthResult requiresAuth(String nonce) {
            return new AuthResult(false, true, nonce, null);
        }

        public static AuthResult failed(String message) {
            return new AuthResult(false, false, null, message);
        }
    }
}

