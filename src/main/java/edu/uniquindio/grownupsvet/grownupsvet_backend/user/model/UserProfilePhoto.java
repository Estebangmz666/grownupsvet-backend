package edu.uniquindio.grownupsvet.grownupsvet_backend.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_profile_photos")
public class UserProfilePhoto {
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfilePhoto() { }

    public UserProfilePhoto(User user, byte[] content, String contentType, int width, int height, Instant updatedAt) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        replace(content, contentType, width, height, updatedAt);
    }

    public void replace(byte[] content, String contentType, int width, int height, Instant updatedAt) {
        this.content = Arrays.copyOf(Objects.requireNonNull(content, "content must not be null"), content.length);
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.sizeBytes = content.length;
        this.width = width;
        this.height = height;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public UUID getUserId() { return userId; }
    public byte[] getContent() { return Arrays.copyOf(content, content.length); }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Instant getUpdatedAt() { return updatedAt; }
}
