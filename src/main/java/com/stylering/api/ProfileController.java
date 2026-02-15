package com.stylering.api;

import com.stylering.api.dto.ProfileResponse;
import com.stylering.profile.PreferenceProfile;
import com.stylering.profile.PreferenceProfileService;
import com.stylering.user.UserAccount;
import com.stylering.user.UserAccountService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProfileController {

    private final PreferenceProfileService preferenceProfileService;
    private final UserAccountService userAccountService;

    public ProfileController(
            PreferenceProfileService preferenceProfileService,
            UserAccountService userAccountService
    ) {
        this.preferenceProfileService = preferenceProfileService;
        this.userAccountService = userAccountService;
    }

    @GetMapping("/profile")
    public ProfileResponse myProfile(Authentication authentication) {
        String firebaseUid = String.valueOf(authentication.getPrincipal());
        UserAccount user = userAccountService.getByFirebaseUid(firebaseUid);
        PreferenceProfile profile = preferenceProfileService.getMyProfile(user);
        return new ProfileResponse(
                profile.getVersion(),
                profile.getProfileJson(),
                profile.getSummary(),
                profile.getUpdatedAt()
        );
    }
}
