package com.atc.trajectory4d.repository;

import com.atc.trajectory4d.model.entity.RestrictedAirspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RestrictedAirspaceRepository extends JpaRepository<RestrictedAirspaceEntity, Long> {

    Optional<RestrictedAirspaceEntity> findByAirspaceId(String airspaceId);

    List<RestrictedAirspaceEntity> findByActiveTrue();

    @Query("SELECT r FROM RestrictedAirspaceEntity r WHERE r.active = true " +
           "AND (r.effectiveFrom IS NULL OR r.effectiveFrom <= :time) " +
           "AND (r.effectiveTo IS NULL OR r.effectiveTo >= :time)")
    List<RestrictedAirspaceEntity> findActiveAtTime(@Param("time") Instant time);

    void deleteByAirspaceId(String airspaceId);
}
