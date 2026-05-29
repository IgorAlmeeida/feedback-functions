package br.com.fiap.feedback.functions;

import br.com.fiap.feedback.entity.Feedback;
import br.com.fiap.feedback.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Azure Function disparada automaticamente quando uma mensagem chega
 * na fila 'feedback-urgente' do Azure Storage Queue.
 *
 * Fluxo: FeedbackService -> QueueService (publica) -> esta function (consome) -> EmailService (envia e-mail)
 *
 * A variável AzureWebJobsStorage é configurada automaticamente pelo Azure Functions
 * e aponta para a Storage Account 'sttechchargerfeedback'.
 */
@ApplicationScoped
public class NotifyUrgentFunction {

    private static final Logger LOG = Logger.getLogger(NotifyUrgentFunction.class);

    @Inject
    EmailService emailService;

    @Inject
    ObjectMapper objectMapper;

    @FunctionName("NotifyUrgent")
    public void run(
            @QueueTrigger(
                    name = "message",
                    queueName = "feedback-urgente",
                    connection = "AzureWebJobsStorage")
            String message,
            final ExecutionContext context) {

        context.getLogger().info("NotifyUrgentFunction acionada. Processando mensagem...");
        LOG.infof("Mensagem recebida da fila: %s", message);

        try {
            Feedback feedback = objectMapper.readValue(message, Feedback.class);

            LOG.infof("Feedback deserializado | id=%d | nota=%d | urgência=%s",
                    feedback.id, feedback.nota, feedback.urgencia);

            emailService.enviarEmailUrgente(feedback);

            context.getLogger().info("E-mail urgente enviado com sucesso. id=" + feedback.id);
            LOG.infof("Notificação urgente concluída com sucesso para feedback id=%d", feedback.id);

        } catch (Exception e) {
            LOG.errorf(e, "Erro ao processar mensagem urgente da fila: %s", message);
            context.getLogger().severe("Falha ao processar mensagem: " + e.getMessage());
            // Re-lança para que o Azure Functions registre a falha e aplique retry policy
            throw new RuntimeException("Falha ao processar mensagem urgente", e);
        }
    }
}
