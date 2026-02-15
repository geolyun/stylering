package com.stylering.user;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;

    public UserAccountService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public UserAccount upsertOnLogin(String firebaseUid) {
        Instant now = Instant.now();
        return userAccountRepository.findByFirebaseUid(firebaseUid)
                .map(existing -> {
                    existing.updateLastLoginAt(now);
                    return existing;
                })
                .orElseGet(() -> userAccountRepository.save(new UserAccount(firebaseUid, now, now)));
    }

    @Transactional(readOnly = true)
    public UserAccount getByFirebaseUid(String firebaseUid) {
        return userAccountRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new IllegalStateException("User account not found"));
    }
}
