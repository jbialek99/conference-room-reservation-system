package org.example.letstry.service;

import org.example.letstry.model.User;
import org.example.letstry.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "booking.mode", havingValue = "demo")
public class DemoIdentityService {

    private static final String TENANT_ID = "00000000-0000-4000-8000-000000000001";
    private final UserRepository userRepository;

    public DemoIdentityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Brak zalogowanego uzytkownika.");
        }
        return findOrCreate(authentication.getName());
    }

    @Transactional
    public User findOrCreate(String username) {
        if (!"demo1".equals(username) && !"demo2".equals(username)) {
            throw new IllegalArgumentException("Nieznany uzytkownik demonstracyjny.");
        }
        String objectId = objectId(username);
        return userRepository.findByTenantIdAndObjectId(TENANT_ID, objectId)
                .orElseGet(() -> {
                    User user = new User();
                    user.setTenantId(TENANT_ID);
                    user.setObjectId(objectId);
                    user.setFirstName(username);
                    user.setLastName("Demo");
                    user.setEmail(username + "@demo.invalid");
                    user.setRole("DEMO");
                    return userRepository.save(user);
                });
    }

    public String tenantId() {
        return TENANT_ID;
    }

    public String objectId(String username) {
        return UUID.nameUUIDFromBytes(("conference-demo:" + username)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Bean
    public ApplicationRunner demoUsersInitializer(DemoIdentityService identities) {
        return arguments -> {
            identities.findOrCreate("demo1");
            identities.findOrCreate("demo2");
        };
    }
}