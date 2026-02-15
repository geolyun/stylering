package com.stylering.api;

import com.stylering.user.UserAccount;
import com.stylering.user.UserAccountService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final UserAccountService userAccountService;

    public MeController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        String firebaseUid = String.valueOf(authentication.getPrincipal());
        UserAccount userAccount = userAccountService.getByFirebaseUid(firebaseUid);
        return new MeResponse(
                userAccount.getFirebaseUid(),
                userAccount.getCreatedAt(),
                userAccount.getLastLoginAt()
        );
    }
}
