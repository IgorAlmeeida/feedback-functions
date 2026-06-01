package br.com.fiap.feedback.service;

import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class KeyVaultService {

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
    }

    public String getSecret(String name) {
        return cache.computeIfAbsent(name, key -> {
            try {
                return secretClient.getSecret(key).getValue();
            } catch (Exception e) {
                throw new RuntimeException("Failed to retrieve secret: " + key, e);
            }
        });
    }

    public void invalidateCache(String name) {
        cache.remove(name);
    }

    public void invalidateAllCache() {
        cache.clear();
    }
}
