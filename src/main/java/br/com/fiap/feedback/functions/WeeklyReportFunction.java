package br.com.fiap.feedback.functions;

import br.com.fiap.feedback.dto.WeeklyReportData;
import br.com.fiap.feedback.service.EmailService;
import br.com.fiap.feedback.service.FeedbackService;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WeeklyReportFunction {

    @Inject
    FeedbackService feedbackService;

    @Inject
    EmailService emailService;

    @FunctionName("WeeklyReport")
    public void run(
            @TimerTrigger(name = "timer", schedule = "%WEEKLY_REPORT_SCHEDULE%")
            String timerInfo,
            final ExecutionContext context) {

        try {
            WeeklyReportData report = feedbackService.generateWeeklyReport();
            emailService.sendWeeklyReport(report);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute WeeklyReport job", e);
        }
    }
}
