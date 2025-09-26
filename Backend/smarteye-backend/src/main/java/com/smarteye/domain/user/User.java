package com.smarteye.domain.user;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.smarteye.domain.analysis.AnalysisJob;
import com.smarteye.domain.book.Book;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @Column(unique = true, nullable = false, length = 100)
    private String email;
    
    @Column(name = "display_name", length = 100)
    private String displayName;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AnalysisJob> analysisJobs = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<Book> books = new ArrayList<>();
    
    // Constructors
    public User() {}
    
    public User(String username, String email) {
        this.username = username;
        this.email = email;
        this.displayName = username;
    }
    
    public User(String username, String email, String displayName) {
        this.username = username;
        this.email = email;
        this.displayName = displayName;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { this.isActive = active; }
    
    // Convenience methods for boolean access
    public boolean isActive() { return isActive != null ? isActive : false; }
    public void setActive(boolean active) { this.isActive = active; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public List<AnalysisJob> getAnalysisJobs() { return analysisJobs; }
    public void setAnalysisJobs(List<AnalysisJob> analysisJobs) { this.analysisJobs = analysisJobs; }
    
    public List<Book> getBooks() { return books; }
    public void setBooks(List<Book> books) { this.books = books; }
    
    // Helper methods
    public void addAnalysisJob(AnalysisJob analysisJob) {
        analysisJobs.add(analysisJob);
        analysisJob.setUser(this);
    }
    
    public void removeAnalysisJob(AnalysisJob analysisJob) {
        analysisJobs.remove(analysisJob);
        analysisJob.setUser(null);
    }
    
    public void addBook(Book book) {
        books.add(book);
        book.setUser(this);
    }
    
    public void removeBook(Book book) {
        books.remove(book);
        book.setUser(null);
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", displayName='" + displayName + '\'' +
                ", isActive=" + isActive +
                ", createdAt=" + createdAt +
                '}';
    }
}