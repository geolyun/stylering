package com.stylering.user;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    public UserAccountService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserAccount upsertOnLogin(String firebaseUid) {
        Object lock = userLocks.computeIfAbsent(firebaseUid, ignored -> new Object());
        synchronized (lock) {
            Instant now = Instant.now();
            return userAccountRepository.findByFirebaseUid(firebaseUid)
                    .map(existing -> {
                        existing.updateLastLoginAt(now);
                        return existing;
                    })
                    .orElseGet(() -> userAccountRepository.save(new UserAccount(firebaseUid, now, now)));
        }
    }

    @Transactional(readOnly = true)
    public UserAccount getByFirebaseUid(String firebaseUid) {
        return userAccountRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new IllegalStateException("User account not found"));
    }
}
