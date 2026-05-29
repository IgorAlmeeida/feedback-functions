package br.com.fiap.feedback.functions;

import br.com.fiap.feedback.service.EmailService;
import br.com.fiap.feedback.service.FeedbackService;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WeeklyReportFunction {

    private static final Logger LOG = Logger.getLogger(WeeklyReportFunction.class);

    @Inject
    FeedbackService feedbackService;

    @Inject
    EmailService emailService;

    @FunctionName("WeeklyReport")
    public void run(
            @TimerTrigger(name = "timer", schedule = "0 0 8 * * 0")
            String timerInfo,
            final ExecutionContext context) {

        context.getLogger().info("WeeklyReportFunction acionada.");
        LOG.info("Iniciando geração do relatório semanal de feedbacks...");

        try {
            String relatorio = feedbackService.gerarRelatorioSemanal();
            LOG.debugf("Relatório gerado:%n%s", relatorio);

            emailService.enviarRelatorioSemanal(relatorio);

            LOG.info("Relatório semanal gerado e enviado com sucesso");
            context.getLogger().info("Relatório semanal enviado com sucesso.");
        } catch (Exception e) {
            LOG.error("Falha ao executar o job de relatório semanal", e);
            context.getLogger().severe("Falha ao gerar relatório semanal: " + e.getMessage());
            throw new RuntimeException("Falha ao executar WeeklyReport", e);
        }
    }
}
