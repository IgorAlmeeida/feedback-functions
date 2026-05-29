package br.com.fiap.feedback.service;

import br.com.fiap.feedback.dto.FeedbackRequest;
import br.com.fiap.feedback.entity.Feedback;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class FeedbackService {

    private static final Logger LOG = Logger.getLogger(FeedbackService.class);

    @Inject
    QueueService queueService;

    @Transactional
    public Feedback salvar(FeedbackRequest request) {
        String urgencia = calcularUrgencia(request.nota);

        Feedback feedback = new Feedback();
        feedback.descricao = request.descricao;
        feedback.nota = request.nota;
        feedback.urgencia = urgencia;

        feedback.persist();

        LOG.infof("Feedback persistido | id=%d | nota=%d | urgência=%s",
                feedback.id, feedback.nota, feedback.urgencia);

        if ("ALTA".equals(urgencia)) {
            queueService.publicarMensagemUrgente(feedback);
        }

        return feedback;
    }

    public String gerarRelatorioSemanal() {
        LocalDateTime inicio = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime fim = LocalDateTime.now();

        List<Feedback> feedbacks = Feedback
                .find("criadoEm >= ?1 and criadoEm <= ?2", inicio, fim)
                .list();

        LOG.infof("Gerando relatório semanal | período: %s a %s | total: %d",
                inicio.toLocalDate(), fim.toLocalDate(), feedbacks.size());

        if (feedbacks.isEmpty()) {
            return "Nenhum feedback recebido no período de " + inicio.toLocalDate()
                    + " a " + fim.toLocalDate() + ".";
        }

        Map<LocalDate, List<Feedback>> porDia = feedbacks.stream()
                .collect(Collectors.groupingBy(f -> f.criadoEm.toLocalDate()));

        Map<String, Long> porUrgencia = feedbacks.stream()
                .collect(Collectors.groupingBy(f -> f.urgencia, Collectors.counting()));

        double mediaNota = feedbacks.stream()
                .mapToInt(f -> f.nota)
                .average()
                .orElse(0.0);

        long totalAlta  = porUrgencia.getOrDefault("ALTA",  0L);
        long totalMedia = porUrgencia.getOrDefault("MEDIA", 0L);
        long totalBaixa = porUrgencia.getOrDefault("BAIXA", 0L);

        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("     RELATÓRIO SEMANAL DE FEEDBACKS     \n");
        sb.append("========================================\n");
        sb.append(String.format("Período:            %s a %s%n", inicio.toLocalDate(), fim.toLocalDate()));
        sb.append(String.format("Total de feedbacks: %d%n", feedbacks.size()));
        sb.append(String.format("Média das notas:    %.2f / 10%n%n", mediaNota));

        sb.append("--- Distribuição por Urgência ---\n");
        sb.append(String.format("  ALTA:   %d%n", totalAlta));
        sb.append(String.format("  MÉDIA:  %d%n", totalMedia));
        sb.append(String.format("  BAIXA:  %d%n%n", totalBaixa));

        sb.append("--- Feedbacks por Dia ---\n");
        porDia.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    double mediaDia = entry.getValue().stream()
                            .mapToInt(f -> f.nota)
                            .average()
                            .orElse(0.0);
                    long altasDia = entry.getValue().stream()
                            .filter(f -> "ALTA".equals(f.urgencia))
                            .count();
                    sb.append(String.format("  %s: %d feedbacks | média: %.2f | urgentes: %d%n",
                            entry.getKey(),
                            entry.getValue().size(),
                            mediaDia,
                            altasDia));
                });

        sb.append("========================================\n");
        return sb.toString();
    }

    private String calcularUrgencia(int nota) {
        if (nota <= 3) return "ALTA";
        if (nota <= 6) return "MEDIA";
        return "BAIXA";
    }
}
