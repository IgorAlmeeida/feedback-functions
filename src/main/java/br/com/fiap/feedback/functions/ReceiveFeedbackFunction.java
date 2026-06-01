package br.com.fiap.feedback.functions;

import br.com.fiap.feedback.dto.FeedbackRequest;
import br.com.fiap.feedback.entity.Feedback;
import br.com.fiap.feedback.service.FeedbackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class ReceiveFeedbackFunction {

    @Inject
    FeedbackService feedbackService;

    @Inject
    ObjectMapper objectMapper;

    @FunctionName("ReceiveFeedback")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "avaliacao")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        String body = request.getBody().orElse(null);

        try {
            if (body == null || body.isBlank()) {
                return badRequest(request, "Corpo da requisição é obrigatório");
            }

            FeedbackRequest feedbackRequest;
            try {
                feedbackRequest = objectMapper.readValue(body, FeedbackRequest.class);
            } catch (Exception e) {
                return badRequest(request, "Corpo da requisição inválido ou mal formatado");
            }

            HttpResponseMessage validationError = validateRequest(request, feedbackRequest);
            if (validationError != null) {
                return validationError;
            }

            Feedback feedback = feedbackService.save(feedbackRequest);

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body(toJson(feedback))
                    .build();

        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(toJson(new ErrorResponse("Erro interno ao processar a avaliação. Tente novamente.")))
                    .build();
        }
    }

    private HttpResponseMessage validateRequest(HttpRequestMessage<Optional<String>> request, FeedbackRequest req) {
        if (req.description == null || req.description.isBlank()) {
            return badRequest(request, "O campo 'descricao' é obrigatório e não pode estar vazio");
        }
        if (req.score == null) {
            return badRequest(request, "O campo 'nota' é obrigatório");
        }
        if (req.score < 0 || req.score > 10) {
            return badRequest(request, "O campo 'nota' deve ser um valor inteiro entre 0 e 10");
        }
        return null;
    }

    private HttpResponseMessage badRequest(HttpRequestMessage<Optional<String>> request, String message) {
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(toJson(new ErrorResponse(message)))
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"erro\":\"Erro de serialização\"}";
        }
    }

    public static class ErrorResponse {
        public final String erro;

        public ErrorResponse(String erro) {
            this.erro = erro;
        }
    }
}
