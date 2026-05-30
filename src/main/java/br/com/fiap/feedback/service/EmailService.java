package br.com.fiap.feedback.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import br.com.fiap.feedback.entity.Feedback;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String FROM_NAME = "Sistema de Feedback - Notificação Urgente";

    @Inject
    KeyVaultService keyVaultService;

    public void enviarEmailUrgente(Feedback feedback) {
        String corpo = construirCorpoEmailUrgente(feedback);
        String assunto = "🚨 Feedback Urgente Recebido";

        try {
            enviar(assunto, corpo);
            LOG.infof("E-mail urgente enviado com sucesso para feedback id=%d", feedback.id);
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao enviar e-mail urgente para feedback id=%d", feedback.id);
            throw new RuntimeException("Falha ao enviar e-mail urgente", e);
        }
    }

    public void enviarRelatorioSemanal(String relatorio) {
        String assunto = "📊 Relatório Semanal de Feedbacks";

        try {
            enviar(assunto, relatorio);
            LOG.info("Relatório semanal enviado com sucesso");
        } catch (Exception e) {
            LOG.error("Falha ao enviar relatório semanal", e);
            throw new RuntimeException("Falha ao enviar relatório semanal", e);
        }
    }

    private void enviar(String assunto, String corpo) throws IOException {
        String apiKey = keyVaultService.getSecret("sendgrid-api-key");
        String fromEmail = keyVaultService.getSecret("sendgrid-from-email");
        String adminEmailsRaw = keyVaultService.getSecret("admin-emails");
        String[] destinatarios = adminEmailsRaw.split(",");

        LOG.infof("Preparando envio | from='%s' | destinatarios=%d | apiKeyPrefix=%s",
                fromEmail, destinatarios.length,
                apiKey != null && apiKey.length() > 5 ? apiKey.substring(0, 5) : "INVALIDA");

        Email remetente = new Email(fromEmail, FROM_NAME);
        Content content = new Content("text/plain", corpo);

        SendGrid sg = new SendGrid(apiKey);

        for (String dest : destinatarios) {
            String email = dest.trim();
            if (email.isBlank()) continue;

            Mail mail = new Mail(remetente, assunto, new Email(email), content);

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);

            // loga SEMPRE como INFO (warn não aparece no Azure) e estoura se falhar
            LOG.infof("SendGrid => destino='%s' | Status=%d | Body=[%s]",
                    email, response.getStatusCode(), response.getBody());

            if (response.getStatusCode() >= 300) {
                throw new IOException("SendGrid recusou envio para " + email
                        + " | Status=" + response.getStatusCode()
                        + " | Body=" + response.getBody());
            }
        }
    }

    private String construirCorpoEmailUrgente(Feedback feedback) {
        String dataFormatada = feedback.criadoEm != null
                ? feedback.criadoEm.format(FORMATTER)
                : "Não informada";

        return "Um feedback com urgência ALTA foi recebido e requer atenção imediata.\n\n"
                + "========================================\n"
                + "DETALHES DO FEEDBACK\n"
                + "========================================\n"
                + "ID:         " + feedback.id + "\n"
                + "Nota:       " + feedback.nota + " / 10\n"
                + "Urgência:   " + feedback.urgencia + "\n"
                + "Data/Hora:  " + dataFormatada + "\n"
                + "========================================\n"
                + "Descrição:\n"
                + feedback.descricao + "\n"
                + "========================================\n\n"
                + "Por favor, tome as ações necessárias o mais breve possível.\n";
    }
}
