package com.pfe.sageline.repository;

import com.pfe.sageline.entity.TicketHandover;
import com.pfe.sageline.entity.User;
import com.pfe.sageline.enums.HandoverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HandoverRepository extends JpaRepository<TicketHandover, Long> {

    @Query("""
        SELECT h FROM TicketHandover h
        WHERE  h.validation.id = :validationId
        AND    h.status IN ('PENDING','ACCEPTED')
    """)
    Optional<TicketHandover> findActiveByValidation(@Param("validationId") Long validationId);

    @Query("""
        SELECT h FROM TicketHandover h
        LEFT JOIN FETCH h.fromTech
        LEFT JOIN FETCH h.toTech
        WHERE  h.validation.id = :validationId
        ORDER BY h.scheduledAt ASC
    """)
    List<TicketHandover> findByValidationOrderByScheduledAtAsc(@Param("validationId") Long validationId);

    @Query("""
        SELECT h FROM TicketHandover h
        LEFT JOIN FETCH h.validation v
        LEFT JOIN FETCH v.productionLine
        LEFT JOIN FETCH h.fromTech
        LEFT JOIN FETCH h.toTech
        WHERE  h.status = 'PENDING'
        ORDER BY h.scheduledAt ASC
    """)
    List<TicketHandover> findAllPending();

    @Query("""
        SELECT h FROM TicketHandover h
        LEFT JOIN FETCH h.validation v
        LEFT JOIN FETCH v.productionLine
        LEFT JOIN FETCH h.fromTech
        LEFT JOIN FETCH h.toTech
        WHERE  h.scheduledAt BETWEEN :from AND :to
    """)
    List<TicketHandover> findInRange(@Param("from") LocalDateTime from,
                                     @Param("to")   LocalDateTime to);

    @Modifying
    @Query("""
        UPDATE TicketHandover h
        SET    h.status = :next, h.toTech = :toTech, h.acceptedAt = :acceptedAt
        WHERE  h.id = :id AND h.status = :expected
    """)
    int compareAndSetStatus(@Param("id")         Long id,
                            @Param("expected")   HandoverStatus expected,
                            @Param("next")       HandoverStatus next,
                            @Param("toTech")     User toTech,
                            @Param("acceptedAt") LocalDateTime acceptedAt);
}
