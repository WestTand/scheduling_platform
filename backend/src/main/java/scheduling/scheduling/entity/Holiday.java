package scheduling.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Entity representing a holiday (ngay le).
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@AttributeOverride(name = "id", column = @Column(name = "holiday_id"))
@Table(name = "holidays",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"holiday_date"})},
        indexes = {@Index(name = "idx_holidays_date", columnList = "holiday_date")})
public class Holiday extends BaseEntity {

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(name = "holiday_name", nullable = false, length = 150)
    private String holidayName;

    @Column(name = "holiday_year", nullable = false)
    private Integer holidayYear;

    @Column(name = "holiday_type", nullable = false, length = 30)
    private String holidayType;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
