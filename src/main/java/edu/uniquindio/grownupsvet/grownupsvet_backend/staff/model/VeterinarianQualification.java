package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "veterinarian_qualifications")
public class VeterinarianQualification {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veterinarian_id", nullable = false, updatable = false) private VeterinarianProfile veterinarian;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private QualificationType type;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 200) private String institution;
    @Column(name = "graduation_year") private Integer graduationYear;
    @Column(name = "base_degree", nullable = false, updatable = false) private boolean baseDegree;
    @Column(name = "diploma_published", nullable = false) private boolean diplomaPublished;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected VeterinarianQualification() { }
    public VeterinarianQualification(VeterinarianProfile veterinarian, QualificationType type, String title,
                                     String institution, Integer graduationYear, boolean baseDegree, Instant now) {
        this.veterinarian = veterinarian; this.baseDegree = baseDegree; this.createdAt = now;
        update(type, title, institution, graduationYear, now);
    }
    public void update(QualificationType type, String title, String institution, Integer graduationYear, Instant now) {
        this.type = type; this.title = title.strip(); this.institution = institution.strip();
        this.graduationYear = graduationYear; this.updatedAt = now;
    }
    public void publishDiploma(boolean published, Instant now) { this.diplomaPublished = published; this.updatedAt = now; }
    public UUID getId() { return id; }
    public VeterinarianProfile getVeterinarian() { return veterinarian; }
    public QualificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getInstitution() { return institution; }
    public Integer getGraduationYear() { return graduationYear; }
    public boolean isBaseDegree() { return baseDegree; }
    public boolean isDiplomaPublished() { return diplomaPublished; }
}
