package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "veterinarian_diplomas")
public class VeterinarianDiploma {
    @Id @Column(name = "qualification_id") private UUID qualificationId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qualification_id", nullable = false, updatable = false) private VeterinarianQualification qualification;
    @Column(nullable = false, columnDefinition = "bytea") private byte[] content;
    @Column(name = "size_bytes", nullable = false) private int sizeBytes;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected VeterinarianDiploma() { }
    public VeterinarianDiploma(VeterinarianQualification qualification, byte[] content, Instant now) {
        this.qualification = qualification; replace(content, now);
    }
    public void replace(byte[] content, Instant now) {
        this.content = Arrays.copyOf(content, content.length); this.sizeBytes = content.length; this.updatedAt = now;
    }
    public byte[] getContent() { return Arrays.copyOf(content, content.length); }
    public int getSizeBytes() { return sizeBytes; }
}
