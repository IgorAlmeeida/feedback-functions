package br.com.fiap.feedback.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedbacks")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Feedback extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @Column(name = "descricao", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    public String description;

    @Column(name = "nota", nullable = false)
    public Integer score;

    @Column(name = "urgencia", nullable = false, length = 10)
    public String urgency;

    @Column(name = "criado_em", nullable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
