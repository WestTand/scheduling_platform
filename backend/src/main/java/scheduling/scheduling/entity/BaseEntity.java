package scheduling.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;

/**
 * Common base entity providing the primary key field.
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;
}
