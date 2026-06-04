package com.replicacao.db.connection;

import com.replicacao.db.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gerencia conexões JDBC separadas para escrita (primário) e leitura (réplicas).
 * As réplicas são selecionadas em round-robin para balancear a carga.
 */
public class ConnectionManager {

    private final AppConfig config;
    private final List<String> replicaUrls = new ArrayList<>();
    private final AtomicInteger replicaIndex = new AtomicInteger(0);

    public ConnectionManager(AppConfig config) {
        this.config = config;

        for (String replica : config.getReadReplicas()) {
            String[] parts = replica.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 3306;
            replicaUrls.add(buildJdbcUrl(host, port, config.getReadDatabase()));
        }

        if (replicaUrls.isEmpty()) {
            throw new IllegalStateException("Nenhuma réplica de leitura configurada em config.properties.");
        }

        System.out.println("✔ Réplicas configuradas: " + replicaUrls.size());
        replicaUrls.forEach(url -> System.out.println("  → " + url));
    }

    /** Retorna uma conexão com o host primário (ESCRITA). */
    public Connection getWriteConnection() throws SQLException {
        String url = buildJdbcUrl(config.getWriteHost(), config.getWritePort(), config.getWriteDatabase());
        return DriverManager.getConnection(url, config.getWriteUsername(), config.getWritePassword());
    }

    /**
     * Retorna uma conexão com a próxima réplica disponível (LEITURA).
     * A seleção é feita em round-robin entre todas as réplicas configuradas.
     */
    public Connection getReadConnection() throws SQLException {
        int idx = Math.abs(replicaIndex.getAndIncrement() % replicaUrls.size());
        String url = replicaUrls.get(idx);
        System.out.println("  [READ] Usando réplica: " + url);
        return DriverManager.getConnection(url, config.getReadUsername(), config.getReadPassword());
    }

    private String buildJdbcUrl(String host, int port, String database) {
        return String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo",
                host, port, database
        );
    }
}
