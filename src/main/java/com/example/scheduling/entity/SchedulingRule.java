package com.example.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Entity representing a scheduling rule (cấu hình ràng buộc).
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "scheduling_rules",
       uniqueConstraints = {@UniqueConstraint(columnNames = {"rule_code"})})
public class SchedulingRule extends BaseEntity {

    @Column(name = "rule_code", nullable = false, length = 50, unique = true)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "rule_type", nullable = false, length = 10)
    private String ruleType; // HARD / SOFT

    @Column(name = "rule_value_json", nullable = false, columnDefinition = "JSONB")
    private String ruleValueJson;

    @Column(name = "weight", precision = 6, scale = 2)
    private BigDecimal weight; // nullable for HARD rules

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;
}
