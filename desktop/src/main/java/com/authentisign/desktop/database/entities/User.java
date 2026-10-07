package com.authentisign.desktop.database.entities;
import jakarta.persistence.*;
import java.time.LocalDateTime;

import lombok.*;


@Entity
@Table(name = "Users")
@Getter
@Setter
@NoArgsConstructor      //מייצר בנאי ריק - במקום לכתוב אותו ידנית
@AllArgsConstructor    //מייצר בנאי שמקבל את כל השדות של המחלקה לפי הסדר
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "Name", nullable = false, columnDefinition = "NVARCHAR(255)")
    private String name;

    @Column(name = "Email", nullable = false,  unique = true)
    private String email;

    @Column(name = "PasswordHash",  nullable = false)
    private String passwordHash;

    @Column(name = "IsActive")
    private boolean isActive;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;


    //פונקציה שמעדכנת תאירך יצירה אוטומטית לפני שמירה
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public User(String name, String email, String passwordHash, boolean isActive, LocalDateTime createdAt) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }
}
