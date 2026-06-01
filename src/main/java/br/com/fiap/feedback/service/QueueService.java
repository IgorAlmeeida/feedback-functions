package br.com.fiap.feedback.service;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.QueueServiceClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.fiap.feedback.entity.Feedback;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class QueueService {

    private static final String QUEUE_NAME = "feedback-urgente";

    @Inject
    ObjectMapper objectMapper;

    private QueueClient queueClient;
    private boolean available = false;

    @PostConstruct
    public void init() {
        String connectionString = System.getenv("AzureWebJobsStorage");
        if (connectionString == null || connectionString.isBlank()) {
            return;
        }

        try {
            QueueServiceClient serviceClient = new QueueServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

            queueClient = serviceClient.getQueueClient(QUEUE_NAME);
            available = true;
        } catch (Exception ignored) {
        }
    }

    public void publishUrgentMessage(Feedback feedback) {
        if (!available || queueClient == null) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(feedback);
            queueClient.sendMessage(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish message to urgency queue", e);
        }
    }
}
