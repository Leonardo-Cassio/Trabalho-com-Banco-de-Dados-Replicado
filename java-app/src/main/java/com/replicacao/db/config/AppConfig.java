package com.replicacao.db.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Lê e expõe as configurações do arquivo config.properties.
 */

public class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        // 1ª prioridade: arquivo externo ao lado do JAR.
        // Permite trocar o IP sem precisar recompilar.
        java.io.File externo = new java.io.File("config.properties");
        if (externo.exists()) {
            try (InputStream is = new java.io.FileInputStream(externo)) {
                props.load(is);
                System.out.println("⚙ Configuração carregada de: " + externo.getAbsolutePath());
                return;
            } catch (IOException e) {
                throw new RuntimeException("Falha ao carregar config.properties externo", e);
            }
        }

        // Segunda prioridade: caso não há arquivo externo, utiliza o config.properties presente no JAR
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) throw new RuntimeException("config.properties não encontrado.");
            props.load(is);
            System.out.println("⚙ Configuração carregada do JAR (embutida).");
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar config.properties", e);
        }
    }

    // ── Host primário (ESCRITA) ───────────────────────────────────────────────
    public String getWriteHost()     { return props.getProperty("db.write.host"); }
    public int    getWritePort()     { return Integer.parseInt(props.getProperty("db.write.port")); }
    public String getWriteDatabase() { return props.getProperty("db.write.database"); }
    public String getWriteUsername() { return props.getProperty("db.write.username"); }
    public String getWritePassword() { return props.getProperty("db.write.password"); }

    // ── Réplicas de leitura (READ) ────────────────────────────────────────────
    public List<String> getReadReplicas() {
        String raw = props.getProperty("db.read.replicas", "");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public String getReadDatabase() { return props.getProperty("db.read.database"); }
    public String getReadUsername() { return props.getProperty("db.read.username"); }
    public String getReadPassword() { return props.getProperty("db.read.password"); }

}
