package com.replicacao.db.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Lê e expõe as configurações do arquivo config.properties.
 *
 * PRIORIDADE DE CARREGAMENTO:
 *   1º — config.properties externo (na mesma pasta de onde o JAR é executado)
 *        → ideal para trocar IPs na apresentação sem recompilar
 *   2º — config.properties embutido dentro do JAR (src/main/resources)
 *        → fallback para desenvolvimento local
 *
 * PROPRIEDADES SUPORTADAS:
 *   db.write.host/port/database/username/password  → host primário (escrita)
 *   db.read.replicas                               → lista "host:porta" separada por vírgula
 *   db.read.database/username/password             → credenciais das réplicas
 *   app.cycle.interval.ms                          → pausa entre ciclos (ms)
 *   app.cycles                                     → total de ciclos (0 = infinito)
 */
public class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        // 1ª prioridade: arquivo externo ao lado do JAR.
        // Permite trocar o IP do professor sem precisar recompilar.
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

        // 2ª prioridade: arquivo embutido no JAR (src/main/resources/config.properties).
        // Usado quando não há arquivo externo — típico durante desenvolvimento.
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
    /**
     * Retorna a lista de réplicas configuradas.
     * O config aceita múltiplas réplicas separadas por vírgula:
     *   db.read.replicas=192.168.1.10:3307,192.168.1.11:3307
     * Cada entrada é lida pelo ConnectionManager para montar a URL JDBC.
     */
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

    // ── Controle de ciclos ────────────────────────────────────────────────────
    /** Pausa em milissegundos entre um ciclo e o próximo (configurável). */
    public long getCycleIntervalMs() {
        return Long.parseLong(props.getProperty("app.cycle.interval.ms", "3000"));
    }

    /** Número total de ciclos. 0 = loop infinito (parar com Ctrl+C). */
    public int getCycles() {
        return Integer.parseInt(props.getProperty("app.cycles", "0"));
    }
}
