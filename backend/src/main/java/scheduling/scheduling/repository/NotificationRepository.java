package scheduling.scheduling.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select n
            from Notification n
            join fetch n.recipient r
            where r.id = :recipientId
              and n.read = coalesce(:read, n.read)
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findForRecipient(@Param("recipientId") Long recipientId, @Param("read") Boolean read);

    @Query("""
            select n
            from Notification n
            join fetch n.recipient r
            where n.id = :id
              and r.id = :recipientId
            """)
    Optional<Notification> findByIdAndRecipientId(@Param("id") Long id, @Param("recipientId") Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.read = true
            where n.recipient.id = :recipientId
              and n.read = false
            """)
    int markAllRead(@Param("recipientId") Long recipientId);

    @Query("""
            select count(n)
            from Notification n
            where n.recipient.id = :recipientId
              and n.read = false
            """)
    long countUnreadForRecipient(@Param("recipientId") Long recipientId);
}
