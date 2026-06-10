package com.replicacao.db.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * CONFIGURAÇÃO DA APLICAÇÃO
 *
 * Lê o arquivo config.properties e expõe as propriedades como métodos tipados.
 * Usada por ConnectionManager para montar as URLs JDBC do primário e das réplicas.
 *
 * PRIORIDADE DE CARREGAMENTO (ordem):
 *   1ª — config.properties no diretório de execução (ao lado do JAR)
 *        → Caminho: java-app/target/config.properties
 *        → Permite trocar hosts na apresentação SEM recompilar
 *
 *   2ª — config.properties embutido dentro do JAR
 *        → Caminho: src/main/resources/config.properties
 *        → Empacotado pelo Maven, serve como fallback
 *
 * COMO TROCAR OS HOSTS SEM RECOMPILAR:
 *   Edite java-app/target/config.properties antes de rodar.
 *   O arquivo externo tem prioridade total sobre o JAR.
 */
public class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        // Verifica se existe um config.properties no diretório atual (onde o JAR está rodando).
        // Isso permite que o professor ou o avaliador troque os hosts sem precisar recompilar.
        java.io.File externo = new java.io.File("config.properties");
        if (externo.exists()) {
            try (InputStream is = new java.io.FileInputStream(externo)) {
                props.load(is);
                System.out.println("⚙ Configuração carregada de: " + externo.getAbsolutePath());
                return; // encontrou externo, não precisa checar o JAR
            } catch (IOException e) {
                throw new RuntimeException("Falha ao carregar config.properties externo", e);
            }
        }

        // Fallback: carrega o config.properties que o Maven empacotou dentro do JAR.
        // getClassLoader().getResourceAsStream() busca dentro do classpath (src/main/resources/).
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) throw new RuntimeException("config.properties não encontrado.");
            props.load(is);
            System.out.println("⚙ Configuração carregada do JAR (embutida).");
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar config.properties", e);
        }
    }

    // ── Getters para o host PRIMÁRIO (usado em getWriteConnection()) ──────────

    /** IP ou hostname do servidor MySQL primário (escrita). */
    public String getWriteHost()     { return props.getProperty("db.write.host"); }

    /** Porta do primário (padrão MySQL: 3306). */
    public int    getWritePort()     { return Integer.parseInt(props.getProperty("db.write.port")); }

    /** Nome do banco de dados no primário (ex: "aula-db"). */
    public String getWriteDatabase() { return props.getProperty("db.write.database"); }

    public String getWriteUsername() { return props.getProperty("db.write.username"); }
    public String getWritePassword() { return props.getProperty("db.write.password"); }

    // ── Getters para as RÉPLICAS (usado em getReadConnection()) ─────────────

    /**
     * Lista de réplicas de leitura no formato "host:porta".
     * Exemplo: ["127.0.0.1:3307", "192.168.1.10:3307"]
     * Lidas do campo db.read.replicas, separadas por vírgula.
     * O ConnectionManager distribui as leituras entre elas em round-robin.
     */
    public List<String> getReadReplicas() {
        String raw = props.getProperty("db.read.replicas", "");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Nome do banco de dados nas réplicas (geralmente igual ao do primário). */
    public String getReadDatabase() { return props.getProperty("db.read.database"); }

    public String getReadUsername() { return props.getProperty("db.read.username"); }
    public String getReadPassword() { return props.getProperty("db.read.password"); }

    // ── Getters de comportamento da aplicação ────────────────────────────────

    /** Intervalo de espera entre ciclos em milissegundos (padrão: 3000ms). */
    public long getCycleIntervalMs() {
        return Long.parseLong(props.getProperty("app.cycle.interval.ms", "3000"));
    }

    /** Quantidade de ciclos a executar. 0 = infinito (roda até Ctrl+C). */
    public int getCycles() {
        return Integer.parseInt(props.getProperty("app.cycles", "0"));
    }
}
