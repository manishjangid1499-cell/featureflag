package com.featureflag.auth_service.repository;

import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByRoleIn(Collection<Role> roles);

    boolean existsByRole(Role role);
}