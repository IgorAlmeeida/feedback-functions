package br.com.fiap.feedback.service;

import br.com.fiap.feedback.dto.FeedbackRequest;
import br.com.fiap.feedback.dto.WeeklyReportData;
import br.com.fiap.feedback.entity.Feedback;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class FeedbackService {

    @Inject
    QueueService queueService;

    @Transactional
    public Feedback save(FeedbackRequest request) {
        String urgency = calculateUrgency(request.score);

        Feedback feedback = new Feedback();
        feedback.description = request.description;
        feedback.score = request.score;
        feedback.urgency = urgency;

        feedback.persist();

        if ("ALTA".equals(urgency)) {
            queueService.publishUrgentMessage(feedback);
        }

        return feedback;
    }

    public WeeklyReportData generateWeeklyReport() {
        LocalDateTime start = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();

        List<Feedback> feedbacks = Feedback
                .find("createdAt >= ?1 and createdAt <= ?2", start, end)
                .list();

        WeeklyReportData data = new WeeklyReportData();
        data.startDate = start.toLocalDate();
        data.endDate = end.toLocalDate();
        data.total = feedbacks.size();

        if (feedbacks.isEmpty()) {
            data.highUrgency = 0;
            data.mediumUrgency = 0;
            data.lowUrgency = 0;
            data.dailyReports = List.of();
            return data;
        }

        Map<String, Long> byUrgency = feedbacks.stream()
                .collect(Collectors.groupingBy(f -> f.urgency, Collectors.counting()));

        data.averageScore = feedbacks.stream().mapToInt(f -> f.score).average().orElse(0.0);
        data.highUrgency = byUrgency.getOrDefault("ALTA", 0L);
        data.mediumUrgency = byUrgency.getOrDefault("MEDIA", 0L);
        data.lowUrgency = byUrgency.getOrDefault("BAIXA", 0L);

        Map<LocalDate, List<Feedback>> byDay = feedbacks.stream()
                .collect(Collectors.groupingBy(f -> f.createdAt.toLocalDate()));

        data.dailyReports = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    double dayAverage = entry.getValue().stream().mapToInt(f -> f.score).average().orElse(0.0);
                    long urgentCount = entry.getValue().stream().filter(f -> "ALTA".equals(f.urgency)).count();
                    return new WeeklyReportData.DayReport(entry.getKey(), entry.getValue().size(), dayAverage, urgentCount);
                })
                .collect(Collectors.toList());

        return data;
    }

    private String calculateUrgency(int score) {
        if (score <= 3) return "ALTA";
        if (score <= 6) return "MEDIA";
        return "BAIXA";
    }
}
