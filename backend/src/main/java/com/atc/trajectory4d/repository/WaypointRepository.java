package com.atc.trajectory4d.repository;

import com.atc.trajectory4d.model.entity.WaypointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WaypointRepository extends JpaRepository<WaypointEntity, Long> {

    List<WaypointEntity> findByFlightPlanIdOrderBySequenceNumber(Long flightPlanId);

    @Query("SELECT w FROM WaypointEntity w WHERE w.flightPlan.flightId = :flightId ORDER BY w.sequenceNumber")
    List<WaypointEntity> findByFlightIdOrderBySequenceNumber(@Param("flightId") String flightId);

    @Query("SELECT w FROM WaypointEntity w WHERE w.name = :name")
    List<WaypointEntity> findByName(@Param("name") String name);

    @Query("SELECT DISTINCT w.name FROM WaypointEntity w WHERE w.name LIKE %:keyword%")
    List<String> findNamesContaining(@Param("keyword") String keyword);

    @Query("SELECT w FROM WaypointEntity w WHERE w.flightPlan.flightId = :flightId AND w.isDeparture = true")
    WaypointEntity findDepartureByFlightId(@Param("flightId") String flightId);

    @Query("SELECT w FROM WaypointEntity w WHERE w.flightPlan.flightId = :flightId AND w.isDestination = true")
    WaypointEntity findDestinationByFlightId(@Param("flightId") String flightId);

    void deleteByFlightPlanId(Long flightPlanId);

    @Query("SELECT COUNT(w) FROM WaypointEntity w WHERE w.flightPlan.flightId = :flightId")
    long countByFlightId(@Param("flightId") String flightId);
}
