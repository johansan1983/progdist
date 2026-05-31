package com.superchat.user.config;

import com.superchat.user.repo.UserProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Tenant-isolation guard used from @PreAuthorize SpEL:
 *   hasRole('PLATFORM_ADMIN') or (hasRole('ORG_ADMIN') and @orgAccess.belongsTo(authentication, #orgId))
 *
 * A bare role check ('is some org admin') is NOT enough for a multi-tenant platform — it lets an
 * ORG_ADMIN of one organization act on the path's {orgId} of ANOTHER organization (cross-tenant IDOR).
 * This verifies the caller's own UserProfile is attached to exactly that organization.
 */
@Component("orgAccess")
public class OrgAccess {

    private final UserProfileRepository profileRepository;

    public OrgAccess(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public boolean belongsTo(Authentication authentication, UUID orgId) {
        if (authentication == null || orgId == null) return false;
        // authentication.getName() is the Keycloak 'sub' (== UserProfile.keycloakId)
        return profileRepository.findByKeycloakId(authentication.getName())
                .map(p -> p.getOrganization() != null && orgId.equals(p.getOrganization().getId()))
                .orElse(false);
    }
}
