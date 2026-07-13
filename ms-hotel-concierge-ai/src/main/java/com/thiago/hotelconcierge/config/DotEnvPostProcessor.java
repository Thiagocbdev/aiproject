package com.thiago.hotelconcierge.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carrega o ficheiro .env da raiz do projecto como PropertySource de baixa prioridade.
 * Variáveis de ambiente do sistema (Docker, IDE) têm sempre precedência.
 * Em Docker o ficheiro não existe no container — não faz nada.
 */
public class DotEnvPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = findEnvFile();
        if (envFile == null) return;

        Map<String, Object> props = new LinkedHashMap<>();
        try {
            Files.lines(envFile)
                .filter(line -> !line.isBlank() && !line.startsWith("#") && line.contains("="))
                .forEach(line -> {
                    int idx = line.indexOf('=');
                    String key   = line.substring(0, idx).trim();
                    String value = line.substring(idx + 1).trim();
                    // env vars do sistema têm precedência — só adiciona se não estiver já definido
                    if (!environment.containsProperty(key)) {
                        props.put(key, value);
                    }
                });
        } catch (IOException ignored) {
            return;
        }

        if (!props.isEmpty()) {
            // addLast = menor prioridade; variáveis do sistema e application.yml ganham sempre
            environment.getPropertySources().addLast(new MapPropertySource("dotenv[.env]", props));
            System.out.printf("[DotEnv] carregado %s (%d vars)%n", envFile, props.size());
        }
    }

    /** Procura .env no cwd e depois no directório pai (raiz do projecto). */
    private Path findEnvFile() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 3; i++) {
            Path candidate = dir.resolve(".env");
            if (Files.isReadable(candidate)) return candidate;
            Path parent = dir.getParent();
            if (parent == null) break;
            dir = parent;
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
