
package org.example.letstry.repository;

import org.example.letstry.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTenantIdAndObjectId(
            String tenantId,
            String objectId
    );

    Optional<User> findByEmail(String email);
}
