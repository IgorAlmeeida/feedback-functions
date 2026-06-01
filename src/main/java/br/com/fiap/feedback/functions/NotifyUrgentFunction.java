package br.com.fiap.feedback.functions;

import br.com.fiap.feedback.entity.Feedback;
import br.com.fiap.feedback.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class NotifyUrgentFunction {

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

        try {
            Feedback feedback = objectMapper.readValue(message, Feedback.class);
            emailService.sendUrgentEmail(feedback);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process urgent queue message", e);
        }
    }
}
