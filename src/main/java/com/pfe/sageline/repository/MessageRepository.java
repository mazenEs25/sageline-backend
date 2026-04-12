package com.pfe.sageline.repository;

import com.pfe.sageline.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderBySentAtAsc(Long conversationId);

    @Query("SELECT COUNT(m) FROM Message m " +
            "WHERE m.conversation.id = :conversationId " +
            "AND m.sender.id != :userId " +
            "AND m.readAt IS NULL")
    int countUnreadInConversation(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId
    );

    @Modifying
    @Query("UPDATE Message m SET m.readAt = :now " +
            "WHERE m.conversation.id = :conversationId " +
            "AND m.sender.id != :userId " +
            "AND m.readAt IS NULL")
    int markAsRead(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    @Query("SELECT COUNT(m) FROM Message m " +
            "JOIN m.conversation c " +
            "WHERE (c.participantOne.id = :userId OR c.participantTwo.id = :userId) " +
            "AND m.sender.id != :userId " +
            "AND m.readAt IS NULL")
    int countTotalUnread(@Param("userId") Long userId);
}