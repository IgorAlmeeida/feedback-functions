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

    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    public String descricao;

    @Column(nullable = false)
    public Integer nota;

    @Column(nullable = false, length = 10)
    public String urgencia;

    @Column(name = "criado_em", nullable = false)
    public LocalDateTime criadoEm;

    @PrePersist
    public void prePersist() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }
}
