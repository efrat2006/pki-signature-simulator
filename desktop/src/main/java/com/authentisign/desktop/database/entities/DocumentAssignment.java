package com.authentisign.desktop.database.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "DocumentAssignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "DocumentId", nullable = false)
    private Document document;

    @ManyToOne
    @JoinColumn(name = "TargetUserId", nullable = false)
    private User targetUser;

    @ManyToOne
    @JoinColumn(name = "AssignedByUserId", nullable = false)
    private User assignedByUser;

    @Column(name = "AssignedAt", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "CompletedAt")
    private LocalDateTime completedAt;

    @Column(name = "IsActive")
    private Boolean isActive = true;

    //ליצירה
    public DocumentAssignment(Document document, User targetUser, User assignedByUser) {
        this.document = document;
        this.targetUser = targetUser;
        this.assignedByUser = assignedByUser;
        this.assignedAt = LocalDateTime.now(); // הזמן הנוכחי
        this.isActive = true;
    }
}
