package edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
}
