package com.featureflag.auth_service.repository;

import com.featureflag.auth_service.entity.SystemInvariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SystemInvariantRepository extends JpaRepository<SystemInvariant, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SystemInvariant s WHERE s.name = :name")
    Optional<SystemInvariant> findByNameForUpdate(@Param("name") String name);
}
