package br.com.fiap.feedback.service;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.core.util.polling.SyncPoller;
import br.com.fiap.feedback.entity.Feedback;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Inject
    KeyVaultService keyVaultService;

    public void enviarEmailUrgente(Feedback feedback) {
        String corpo = construirCorpoEmailUrgente(feedback);
        try {
            enviar("Feedback Urgente Recebido", corpo);
            LOG.infof("E-mail urgente enviado com sucesso para feedback id=%d", feedback.id);
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao enviar e-mail urgente para feedback id=%d", feedback.id);
            throw new RuntimeException("Falha ao enviar e-mail urgente", e);
        }
    }

    public void enviarRelatorioSemanal(String relatorio) {
        try {
            enviar("Relatorio Semanal de Feedbacks", relatorio);
            LOG.info("Relatório semanal enviado com sucesso");
        } catch (Exception e) {
            LOG.error("Falha ao enviar relatório semanal", e);
            throw new RuntimeException("Falha ao enviar relatório semanal", e);
        }
    }

    private void enviar(String assunto, String corpo) {
        String connectionString = keyVaultService.getSecret("acs-connection-string");
        String remetente = keyVaultService.getSecret("acs-sender-email");
        String adminEmailsRaw = keyVaultService.getSecret("admin-emails");

        LOG.infof("Preparando envio ACS | from='%s'", remetente);

        EmailClient client = new EmailClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        for (String dest : adminEmailsRaw.split(",")) {
            String email = dest.trim();
            if (email.isBlank()) continue;

            EmailMessage message = new EmailMessage()
                    .setSenderAddress(remetente)
                    .setToRecipients(email)
                    .setSubject(assunto)
                    .setBodyPlainText(corpo);

            SyncPoller<EmailSendResult, EmailSendResult> poller = client.beginSend(message, null);
            EmailSendResult result = poller.waitForCompletion().getValue();

            LOG.infof("ACS => destino='%s' | Status=%s | Id=%s",
                    email, result.getStatus(), result.getId());
        }
    }

    private String construirCorpoEmailUrgente(Feedback feedback) {
        String data = feedback.criadoEm != null
                ? feedback.criadoEm.format(FORMATTER)
                : "Não informada";

        return "Um feedback com urgência ALTA foi recebido e requer atenção imediata.\n\n"
                + "========================================\n"
                + "DETALHES DO FEEDBACK\n"
                + "========================================\n"
                + "ID:         " + feedback.id + "\n"
                + "Nota:       " + feedback.nota + " / 10\n"
                + "Urgência:   " + feedback.urgencia + "\n"
                + "Data/Hora:  " + data + "\n"
                + "========================================\n"
                + "Descrição:\n"
                + feedback.descricao + "\n"
                + "========================================\n\n"
                + "Por favor, tome as ações necessárias o mais breve possível.\n";
    }
}