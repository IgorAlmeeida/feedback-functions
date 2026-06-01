package br.com.fiap.feedback.service;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.core.util.polling.SyncPoller;
import br.com.fiap.feedback.dto.WeeklyReportData;
import br.com.fiap.feedback.entity.Feedback;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class EmailService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Inject
    KeyVaultService keyVaultService;

    @Inject
    TemplateService templateService;

    public void sendUrgentEmail(Feedback feedback) {
        Context ctx = new Context();
        ctx.setVariable("feedback", feedback);
        ctx.setVariable("formattedDate", feedback.createdAt != null
                ? feedback.createdAt.format(DATE_FORMATTER) : "Não informada");

        String html = templateService.render("urgent-feedback-email", ctx);
        send("Feedback Urgente Recebido", html);
    }

    public void sendWeeklyReport(WeeklyReportData report) {
        Context ctx = new Context();
        ctx.setVariable("report", report);

        String html = templateService.render("weekly-report-email", ctx);
        send("Relatório Semanal de Feedbacks", html);
    }

    private void send(String subject, String htmlBody) {
        String connectionString = keyVaultService.getSecret("acs-connection-string");
        String senderAddress = keyVaultService.getSecret("acs-sender-email");
        String adminEmailsRaw = keyVaultService.getSecret("admin-emails");

        EmailClient client = new EmailClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        for (String recipient : adminEmailsRaw.split(",")) {
            String email = recipient.trim();
            if (email.isBlank()) continue;

            EmailMessage message = new EmailMessage()
                    .setSenderAddress(senderAddress)
                    .setToRecipients(email)
                    .setSubject(subject)
                    .setBodyHtml(htmlBody);

            SyncPoller<EmailSendResult, EmailSendResult> poller = client.beginSend(message, null);
            poller.waitForCompletion();
        }
    }
}
