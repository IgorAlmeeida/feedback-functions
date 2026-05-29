package br.com.fiap.feedback.service;

import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class KeyVaultService {

    private static final Logger LOG = Logger.getLogger(KeyVaultService.class);

    @ConfigProperty(name = "azure.keyvault.url")
    String keyVaultUrl;

    private SecretClient secretClient;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        secretClient = new SecretClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(new ManagedIdentityCredentialBuilder().build())
                .buildClient();
        LOG.infof("KeyVaultService inicializado. Vault: %s", keyVaultUrl);
    }

    public String getSecret(String name) {
        return cache.computeIfAbsent(name, key -> {
            try {
                String value = secretClient.getSecret(key).getValue();
                LOG.infof("Secret '%s' carregado do Key Vault", key);
                return value;
            } catch (Exception e) {
                LOG.errorf(e, "Falha ao buscar secret '%s' do Key Vault", key);
                throw new RuntimeException("Falha ao buscar secret: " + key, e);
            }
        });
    }

    public void invalidarCache(String name) {
        cache.remove(name);
        LOG.infof("Cache do secret '%s' invalidado", name);
    }

    public void invalidarTodoCache() {
        cache.clear();
        LOG.info("Todo o cache de secrets foi invalidado");
    }
}
