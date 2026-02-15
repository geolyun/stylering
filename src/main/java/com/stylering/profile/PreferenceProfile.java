package com.stylering.profile;

import com.stylering.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "preference_profiles")
public class PreferenceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false)
    private int version;

    @Lob
    @Column(nullable = false)
    private String profileJson;

    @Lob
    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PreferenceProfile() {
    }

    public PreferenceProfile(UserAccount user, int version, String profileJson, String summary) {
        this.user = user;
        this.version = version;
        this.profileJson = profileJson;
        this.summary = summary;
    }

    @PrePersist
    void onCreate() {
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void update(int nextVersion, String nextProfileJson, String nextSummary) {
        this.version = nextVersion;
        this.profileJson = nextProfileJson;
        this.summary = nextSummary;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public int getVersion() {
        return version;
    }

    public String getProfileJson() {
        return profileJson;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
