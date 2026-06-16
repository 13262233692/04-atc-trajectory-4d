package com.atc.trajectory4d.repository;

import com.atc.trajectory4d.model.entity.TrajectoryPointEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrajectoryPointRepository extends JpaRepository<TrajectoryPointEntity, Long> {

    List<TrajectoryPointEntity> findByFlightIdOrderBySequenceNumber(String flightId);

    List<TrajectoryPointEntity> findByFlightIdOrderBySequenceNumberAsc(String flightId, Pageable pageable);

    @Query("SELECT t FROM TrajectoryPointEntity t WHERE t.flightId = :flightId AND t.sequenceNumber >= :startSeq ORDER BY t.sequenceNumber")
    List<TrajectoryPointEntity> findByFlightIdAndSequenceNumberGreaterThanEqual(
            @Param("flightId") String flightId,
            @Param("startSeq") long startSeq
    );

    @Query("SELECT t FROM TrajectoryPointEntity t WHERE t.flightId = :flightId ORDER BY t.sequenceNumber DESC LIMIT 1")
    Optional<TrajectoryPointEntity> findLatestByFlightId(@Param("flightId") String flightId);

    @Query("SELECT t FROM TrajectoryPointEntity t WHERE t.flightPlan.flightId = :flightId ORDER BY t.sequenceNumber")
    List<TrajectoryPointEntity> findByFlightPlanIdOrderBySequenceNumber(@Param("flightId") String flightId);

    @Query("SELECT t FROM TrajectoryPointEntity t WHERE t.flightId = :flightId AND t.timestamp BETWEEN :start AND :end ORDER BY t.timestamp")
    List<TrajectoryPointEntity> findByFlightIdAndTimestampBetween(
            @Param("flightId") String flightId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT t FROM TrajectoryPointEntity t WHERE t.flightPhase = :phase ORDER BY t.timestamp DESC")
    List<TrajectoryPointEntity> findByFlightPhase(@Param("phase") String phase, Pageable pageable);

    @Query("SELECT DISTINCT t.flightId FROM TrajectoryPointEntity t WHERE t.timestamp > :since")
    List<String> findActiveFlightIds(@Param("since") LocalDateTime since);

    @Query("SELECT MAX(t.sequenceNumber) FROM TrajectoryPointEntity t WHERE t.flightId = :flightId")
    Long findMaxSequenceNumber(@Param("flightId") String flightId);

    @Query("SELECT COUNT(t) FROM TrajectoryPointEntity t WHERE t.flightId = :flightId")
    long countByFlightId(@Param("flightId") String flightId);

    void deleteByFlightId(String flightId);

    @Query("SELECT t FROM TrajectoryPointEntity t WHERE " +
           "t.latitude BETWEEN :minLat AND :maxLat AND " +
           "t.longitude BETWEEN :minLon AND :maxLon AND " +
           "t.altitude BETWEEN :minAlt AND :maxAlt AND " +
           "t.timestamp BETWEEN :start AND :end " +
           "ORDER BY t.timestamp")
    List<TrajectoryPointEntity> findInAreaAndTime(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("minAlt") double minAlt,
            @Param("maxAlt") double maxAlt,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
