# Plataforma de Feedback — Tech Challenge Fase 4

Plataforma serverless no Azure para coleta e análise de feedbacks de aulas. Estudantes enviam avaliações via API, administradores recebem notificações automáticas de feedbacks críticos e um relatório semanal consolidado por e-mail.

---

## Sumário

- [Arquitetura](#arquitetura)
- [Modelo de cloud](#modelo-de-cloud)
- [Componentes](#componentes)
- [Funções serverless](#funções-serverless)
- [Segurança e governança](#segurança-e-governança)
- [Monitoramento](#monitoramento)
- [Provisionamento do ambiente (passo a passo)](#provisionamento-do-ambiente-passo-a-passo)
- [Deploy](#deploy)
- [Execução local](#execução-local)
- [Endpoints](#endpoints)
- [Stack](#stack)
- [Estrutura do projeto](#estrutura-do-projeto)

---

## Arquitetura

```
                          ┌──────────────┐
                          │   Cliente    │
                          └──────┬───────┘
                                 │ POST /api/avaliacao
                                 ▼
                    ┌────────────────────────┐
                    │   fn ReceiveFeedback    │
                    │   (HTTP Trigger)        │
                    │  valida · classifica ·  │
                    │  persiste               │
                    └──────┬───────────┬──────┘
                           │           │
              salva todos  │           │ se urgência = ALTA
                           ▼           ▼
                 ┌─────────────┐  ┌──────────────────┐
                 │  Azure SQL  │  │  Storage Queue    │
                 │  Database   │  │ feedback-urgente  │
                 └──────┬──────┘  └────────┬──────────┘
                        │                  │ trigger
                        │                  ▼
                        │        ┌────────────────────┐
                        │        │  fn NotifyUrgent    │
                        │        │  (Queue Trigger)    │
                        │        └─────────┬──────────┘
       todo domingo 08h │                  │
                        ▼                  ▼
              ┌────────────────────┐  ┌──────────────────┐
              │  fn WeeklyReport    │  │  Azure            │
              │  (Timer Trigger)    │─▶│  Communication    │
              └────────────────────┘  │  Services (Email) │
                        │             └─────────┬─────────┘
                        ▼                       ▼
              ┌────────────────────┐  ┌──────────────────┐
              │   Azure Key Vault   │  │  Administradores  │
              └────────────────────┘  └──────────────────┘

   Observabilidade: Application Insights + Azure Monitor
   CI/CD: GitHub Actions → Azure Functions
```

O cliente envia uma avaliação ao endpoint HTTP. A função `ReceiveFeedback` valida, classifica a urgência pela nota, persiste no SQL Database e — se a urgência for ALTA — publica na fila. A função `NotifyUrgent` consome a fila e envia o e-mail de alerta. A função `WeeklyReport` roda semanalmente, agrega os dados e envia o relatório. A fila desacopla o recebimento do envio de e-mail, garantindo resiliência.

---

## Modelo de cloud

A solução usa **Serverless (Azure Functions, plano Consumption)**. Escolhido por: faturamento por execução (sem custo ocioso), escalonamento automático sem gerenciar servidores, separação natural em funções de responsabilidade única e integração nativa com os demais serviços Azure.

---

## Componentes

| Componente | Serviço Azure | Responsabilidade |
|---|---|---|
| Compute serverless | Azure Functions (Consumption) | Hospeda as três funções |
| Banco de dados | Azure SQL Database | Persistência dos feedbacks |
| Fila de mensagens | Azure Storage Queue | Desacopla recebimento de notificação |
| Envio de e-mail | Azure Communication Services | Notificações e relatórios |
| Cofre de segredos | Azure Key Vault | Connection strings e credenciais |
| Observabilidade | Application Insights + Azure Monitor | Logs, métricas e alertas |
| CI/CD | GitHub Actions | Deploy automatizado |

---

## Funções serverless

Três funções, cada uma com responsabilidade única.

**ReceiveFeedback (HTTP Trigger)** — Recebe avaliações via `POST /api/avaliacao`. Valida o payload, calcula a urgência, persiste no banco e publica na fila se a urgência for ALTA. Retorna 201 (sucesso), 400 (payload inválido) ou 500 (erro interno).

**NotifyUrgent (Queue Trigger)** — Acionada pela fila `feedback-urgente`. Envia e-mail imediato aos administradores com descrição, urgência e data do feedback crítico.

**WeeklyReport (Timer Trigger)** — Roda todo domingo às 08:00 (Brasília). Consulta os feedbacks da semana e envia relatório com quantidade por dia, quantidade por urgência e média das notas.

### Classificação de urgência

| Nota | Urgência | Ação |
|---|---|---|
| 0–3 | ALTA | Persiste e publica na fila (notifica) |
| 4–6 | MEDIA | Persiste apenas |
| 7–10 | BAIXA | Persiste apenas |

---

## Segurança e governança

Nenhuma credencial fica no código. Todos os segredos (connection strings, chaves) ficam no **Key Vault**, acessados via **Managed Identity** — sem senhas no código. A governança usa **RBAC**, concedendo à aplicação apenas leitura de segredos. A conexão com o banco é criptografada (TLS) e protegida por firewall. As variáveis de ambiente referenciam os segredos por `@Microsoft.KeyVault(...)`, sem expor valores.

---

## Monitoramento

O **Application Insights** coleta logs, requisições, métricas e exceções de cada função. A inspeção é feita pelas seções de Falhas, Desempenho e Logs (consultas KQL):

```kql
traces
| where timestamp > ago(1h)
| order by timestamp desc
| project timestamp, message, severityLevel
```

O **Azure Monitor** permite alertas (ex.: notificar quando uma função falhar), e um **Budget Alert** no Cost Management avisa sobre custos inesperados.

---

## Provisionamento do ambiente (passo a passo)

Esta seção documenta, na ordem em que foram executadas, todas as etapas de criação da infraestrutura no portal do Azure. Os nomes correspondem ao ambiente real do projeto.

**1. Conta e proteção de custos.** Criação da conta Azure e de um **Budget Alert** no Cost Management para notificar sobre qualquer custo inesperado, dado que o ambiente possui créditos limitados.

**2. Resource Group.** Criação do grupo de recursos `tech-charger4`, que agrupa todos os componentes da solução.

**3. Servidor de banco de dados.** Criação do Azure SQL Server `tech-charger-feedback-2026` com autenticação SQL. Connection string: `jdbc:sqlserver://tech-charger-feedback-2026.database.windows.net:1433;databaseName=tech-charger4-feedback;encrypt=true;trustServerCertificate=false;`. As credenciais de administrador foram definidas na criação e armazenadas no Key Vault (nunca no código).

**4. Banco de dados.** Criação do Azure SQL Database `tech-charger4-feedback` no tier gratuito (General Purpose Serverless), com auto-pausa habilitada para não gerar custos durante a inatividade.

**5. Key Vault.** Criação do cofre `kvTechCharger4Feedback26` para centralizar segredos e connection strings.

**6. Permissão de acesso ao Key Vault (usuário).** Configuração de política de acesso concedendo ao usuário administrador permissão de gerenciamento de segredos no cofre.

**7. Conta de armazenamento.** Criação da Storage Account `sttechchargerfeedback`, que hospeda a fila de urgência e dá suporte ao runtime do Function App.

**8. Fila de mensagens.** Criação da fila `feedback-urgente`, responsável por desacoplar o recebimento do feedback do envio da notificação.

**9. Function App.** Criação do Function App no plano **Consumo (Windows)** com runtime Java 17, habilitando o **Application Insights** já na criação.

**10. Managed Identity.** Habilitação da identidade gerenciada atribuída pelo sistema no Function App (Object ID `9da1c269-ca67-416f-96c3-10540a6a4fff`), permitindo acesso a outros recursos sem credenciais no código.

**11. Permissão de acesso ao Key Vault (aplicação).** Concessão à Managed Identity do Function App de permissão de leitura de segredos no Key Vault.

**12. Implantação da aplicação.** Desenvolvimento e publicação das três funções serverless.

**13. Serviço de e-mail.** A abordagem inicial usou SendGrid (criação de API Key e atualização do segredo no Key Vault). Posteriormente o envio foi migrado para o **Azure Communication Services**, serviço nativo do Azure: criação do Email Communication Service, provisionamento de domínio gerenciado e conexão ao Communication Services, com as credenciais atualizadas no Key Vault.

**14. Variáveis de ambiente.** Configuração das variáveis do Function App referenciando os segredos via `@Microsoft.KeyVault(VaultName=...;SecretName=...)`.

**15. Repositório e CI/CD.** Criação do repositório no GitHub, configuração do workflow do GitHub Actions e ajuste da autenticação para usar `azure/login` com Service Principal.

---

## Deploy

Automatizado via **GitHub Actions**: a cada push na `main`, o workflow compila, empacota e publica no Azure Functions.

**1.** Gere o Service Principal:

```bash
az ad sp create-for-rbac --name "github-deploy" \
  --role contributor \
  --scopes /subscriptions/<SUBSCRIPTION_ID>/resourceGroups/<RESOURCE_GROUP> \
  --sdk-auth
```

**2.** No GitHub (**Settings → Secrets → Actions**), crie o secret `AZURE_CREDENTIALS` com o JSON retornado.

**3.** O workflow (`.github/workflows/deploy.yml`) publica automaticamente:

```yaml
name: Deploy Azure Functions
on:
  push:
    branches: [main]
jobs:
  build-and-deploy:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: mvn clean package -DskipTests
      - uses: azure/login@v1
        with:
          creds: ${{ secrets.AZURE_CREDENTIALS }}
      - uses: Azure/functions-action@v1
        with:
          app-name: <NOME_DO_FUNCTION_APP>
          package: target/azure-functions/<NOME_DO_FUNCTION_APP>
```

### Segredos necessários no Key Vault

| Secret | Variável de ambiente | Descrição |
|---|---|---|
| `db-url` | `DB_URL` | Connection string JDBC |
| `db-username` | `DB_USERNAME` | Usuário do banco |
| `db-password` | `DB_PASSWORD` | Senha do banco |
| `acs-connection-string` | — | Connection string do Communication Services |
| `acs-sender-email` | — | Remetente verificado |
| `admin-emails` | `ADMIN_EMAILS` | E-mails dos administradores (separados por vírgula) |

As migrações são aplicadas automaticamente pelo **Liquibase** na inicialização.

---

## Execução local

```bash
git clone <URL_DO_REPOSITORIO>
cd feedback-functions

export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=feedback;encrypt=false;trustServerCertificate=true;"
export DB_USERNAME="sa"
export DB_PASSWORD="<sua-senha>"

mvn quarkus:dev
```

O Liquibase cria a estrutura do banco na primeira execução.

---

## Endpoints

```http
POST /api/avaliacao
Content-Type: application/json

{
  "descricao": "A aula foi excelente e muito didática",
  "nota": 9
}
```

Resposta (201):

```json
{
  "id": 1,
  "descricao": "A aula foi excelente e muito didática",
  "nota": 9,
  "urgencia": "BAIXA",
  "criadoEm": "2026-05-31T22:15:00"
}
```

Nota entre 0 e 3 dispara notificação imediata por e-mail.

---

## Stack

Java 17 · Quarkus · Hibernate ORM with Panache · Liquibase · Azure SQL Database · Azure Functions (Consumption) · Azure Storage Queue · Azure Communication Services · Azure Key Vault · Managed Identity + RBAC · Application Insights · GitHub Actions · Maven

---

## Estrutura do projeto

```
src/main/java/br/com/fiap/feedback/
├── dto/FeedbackRequest.java          # DTO de entrada
├── entity/Feedback.java              # Entidade JPA / Panache
├── functions/
│   ├── ReceiveFeedbackFunction.java  # HTTP Trigger
│   ├── NotifyUrgentFunction.java     # Queue Trigger
│   └── WeeklyReportFunction.java     # Timer Trigger
└── service/
    ├── FeedbackService.java          # Regras de negócio e relatório
    ├── QueueService.java             # Publicação na fila
    ├── EmailService.java             # Envio de e-mail (ACS)
    └── KeyVaultService.java          # Leitura de segredos

src/main/resources/
├── application.properties
└── db/changelog/db.changelog-master.xml

.github/workflows/deploy.yml          # Pipeline CI/CD
```
