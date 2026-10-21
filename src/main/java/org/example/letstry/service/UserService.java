
package org.example.letstry.service;

import org.example.letstry.model.User;
import org.example.letstry.repository.UserRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findUserById(Long userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public void addUserAttributesToModel(OAuth2User principal, Model model) {
        if (principal != null) {
            model.addAttribute("username", principal.getAttribute("name"));
            model.addAttribute("email", principal.getAttribute("email"));
        }
    }

    @Transactional
    public User createUserIfNotExists(OAuth2User principal) {
        String tenantId = getRequiredGuidClaim(principal, "tid");
        String objectId = getRequiredGuidClaim(principal, "oid");

        return userRepository.findByTenantIdAndObjectId(tenantId, objectId)
                .orElseGet(() -> createUser(principal, tenantId, objectId));
    }

    private User createUser(
            OAuth2User principal,
            String tenantId,
            String objectId
    ) {
        User user = new User();

        user.setTenantId(tenantId);
        user.setObjectId(objectId);
        user.setFirstName(getOptionalClaim(principal, "given_name"));
        user.setLastName(getOptionalClaim(principal, "family_name"));
        user.setEmail(getOptionalClaim(principal, "email"));

        return userRepository.save(user);
    }

    private String getRequiredGuidClaim(OAuth2User principal, String claimName) {
        if (principal == null) {
            throw new IllegalArgumentException("Missing authenticated principal");
        }

        Object value = principal.getAttribute(claimName);

        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Missing required OIDC claim: " + claimName);
        }

        try {
            return UUID.fromString(text).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid OIDC claim: " + claimName, exception);
        }
    }

    private String getOptionalClaim(OAuth2User principal, String claimName) {
        String value = principal.getAttribute(claimName);
        return value != null ? value : "";
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}
