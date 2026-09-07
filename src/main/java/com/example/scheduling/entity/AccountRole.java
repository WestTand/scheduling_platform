package com.example.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import java.util.Set;

/**
 * Enum representing possible account roles.
 */
@Getter
@AllArgsConstructor
public enum AccountRole {
    MANAGER("MANAGER"),
    EMPLOYEE("EMPLOYEE");

    private final String value;
}
