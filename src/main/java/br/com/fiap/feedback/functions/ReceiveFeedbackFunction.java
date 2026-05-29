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
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class ReceiveFeedbackFunction {

    private static final Logger LOG = Logger.getLogger(ReceiveFeedbackFunction.class);

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

        context.getLogger().info("ReceiveFeedbackFunction acionada.");

        String body = request.getBody().orElse(null);
        LOG.debugf("Recebendo avaliação | body length=%d", body != null ? body.length() : 0);

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

            HttpResponseMessage validacao = validarRequest(request, feedbackRequest);
            if (validacao != null) {
                return validacao;
            }

            Feedback feedback = feedbackService.salvar(feedbackRequest);
            LOG.infof("Avaliação registrada com sucesso | id=%d | urgência=%s", feedback.id, feedback.urgencia);
            context.getLogger().info("Feedback registrado. id=" + feedback.id);

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body(toJson(feedback))
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Erro interno ao processar avaliação");
            context.getLogger().severe("Erro interno: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(toJson(new ErrorResponse("Erro interno ao processar a avaliação. Tente novamente.")))
                    .build();
        }
    }

    private HttpResponseMessage validarRequest(HttpRequestMessage<Optional<String>> request, FeedbackRequest req) {
        if (req.descricao == null || req.descricao.isBlank()) {
            return badRequest(request, "O campo 'descricao' é obrigatório e não pode estar vazio");
        }
        if (req.nota == null) {
            return badRequest(request, "O campo 'nota' é obrigatório");
        }
        if (req.nota < 0 || req.nota > 10) {
            return badRequest(request, "O campo 'nota' deve ser um valor inteiro entre 0 e 10");
        }
        return null;
    }

    private HttpResponseMessage badRequest(HttpRequestMessage<Optional<String>> request, String mensagem) {
        LOG.warnf("Requisição inválida: %s", mensagem);
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(toJson(new ErrorResponse(mensagem)))
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
