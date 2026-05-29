package br.com.fiap.feedback.service;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.QueueServiceClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.fiap.feedback.entity.Feedback;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class QueueService {

    private static final Logger LOG = Logger.getLogger(QueueService.class);
    private static final String QUEUE_NAME = "feedback-urgente";

    @Inject
    ObjectMapper objectMapper;

    private QueueClient queueClient;
    private boolean disponivel = false;

    @PostConstruct
    public void init() {
        String connectionString = System.getenv("AzureWebJobsStorage");
        if (connectionString == null || connectionString.isBlank()) {
            LOG.warn("Variável AzureWebJobsStorage não configurada - QueueService indisponível em ambiente local");
            return;
        }

        try {
            QueueServiceClient serviceClient = new QueueServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

            queueClient = serviceClient.getQueueClient(QUEUE_NAME);
            disponivel = true;
            LOG.infof("QueueService inicializado. Fila: %s", QUEUE_NAME);
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao inicializar QueueService");
        }
    }

    public void publicarMensagemUrgente(Feedback feedback) {
        if (!disponivel || queueClient == null) {
            LOG.warnf("QueueService indisponível - mensagem do feedback id=%d não publicada", feedback.id);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(feedback);
            queueClient.sendMessage(json);
            LOG.infof("Mensagem publicada na fila '%s' para feedback id=%d urgência=%s",
                    QUEUE_NAME, feedback.id, feedback.urgencia);
        } catch (Exception e) {
            LOG.errorf(e, "Erro ao publicar mensagem na fila para feedback id=%d", feedback.id);
            throw new RuntimeException("Falha ao publicar mensagem na fila de urgência", e);
        }
    }
}
