package com.pfe.sageline.repository;

import com.pfe.sageline.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c " +
            "WHERE c.participantOne.id = :userId OR c.participantTwo.id = :userId " +
            "ORDER BY c.updatedAt DESC")
    List<Conversation> findByUserId(@Param("userId") Long userId);

    @Query("SELECT c FROM Conversation c " +
            "WHERE (c.participantOne.id = :userOneId AND c.participantTwo.id = :userTwoId) " +
            "OR (c.participantOne.id = :userTwoId AND c.participantTwo.id = :userOneId)")
    Optional<Conversation> findByParticipants(
            @Param("userOneId") Long userOneId,
            @Param("userTwoId") Long userTwoId
    );

    @Query("SELECT c FROM Conversation c " +
            "WHERE ((c.participantOne.id = :userOneId AND c.participantTwo.id = :userTwoId) " +
            "OR (c.participantOne.id = :userTwoId AND c.participantTwo.id = :userOneId)) " +
            "AND c.referenceId = :refId AND c.referenceType = :refType")
    Optional<Conversation> findByParticipantsAndReference(
            @Param("userOneId") Long userOneId,
            @Param("userTwoId") Long userTwoId,
            @Param("refId") Long refId,
            @Param("refType") String refType
    );
}