package com.uav.iotbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "users")
@Getter @Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    private String email;
    private String role;   // ADMIN, OPERATOR, TECHNICIAN
    private String status; // ACTIVE, INACTIVE, LOCKED

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}