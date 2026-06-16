package com.atc.trajectory4d.repository;

import com.atc.trajectory4d.model.entity.FlightPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightPlanRepository extends JpaRepository<FlightPlanEntity, Long> {

    Optional<FlightPlanEntity> findByFlightId(String flightId);

    boolean existsByFlightId(String flightId);

    List<FlightPlanEntity> findByStatus(String status);

    List<FlightPlanEntity> findByDepartureAirport(String departureAirport);

    List<FlightPlanEntity> findByArrivalAirport(String arrivalAirport);

    @Query("SELECT f FROM FlightPlanEntity f WHERE f.departureTime BETWEEN :start AND :end")
    List<FlightPlanEntity> findByDepartureTimeBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT f FROM FlightPlanEntity f WHERE f.aircraftType = :aircraftType")
    List<FlightPlanEntity> findByAircraftType(@Param("aircraftType") String aircraftType);

    @Query("SELECT DISTINCT f.airline FROM FlightPlanEntity f")
    List<String> findAllAirlines();

    @Query("SELECT COUNT(f) FROM FlightPlanEntity f WHERE f.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT f FROM FlightPlanEntity f JOIN FETCH f.waypoints WHERE f.flightId = :flightId")
    Optional<FlightPlanEntity> findByFlightIdWithWaypoints(@Param("flightId") String flightId);

    void deleteByFlightId(String flightId);
}
