package com.replicacao.db.connection;

import com.replicacao.db.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Esta classe é responsável por garantir que toda operação escrita vá para o host PRIMÁRIO e toda operação de leitura vá para uma RÉPLICA.:
 *   - Todo INSERT, UPDATE, DELETE vá para o host PRIMÁRIO (via getWriteConnection)
 *   - Todo SELECT vá para uma RÉPLICA (via getReadConnection)
 */

public class ConnectionManager {

    private final AppConfig config;

    // Lista de URLs JDBC das réplicas.
    private final List<String> replicaUrls = new ArrayList<>();

    private final AtomicInteger replicaIndex = new AtomicInteger(0);

    public ConnectionManager(AppConfig config) {
        this.config = config;

        // Monta as URLs JDBC para cada réplica.
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

    //getWriteConnection() é chamado por: cadastrarCliente(), cadastrarProduto(), criarPedido(), etc.
    public Connection getWriteConnection() throws SQLException {
        String url = buildJdbcUrl(config.getWriteHost(), config.getWritePort(), config.getWriteDatabase());
        return DriverManager.getConnection(url, config.getWriteUsername(), config.getWritePassword());
    }

    //getReadConnection() é chamado por: listarTodos(), buscarPorId(), historicoPorCliente(), etc.
    public Connection getReadConnection() throws SQLException {
        // Math.abs evita índice negativo caso o AtomicInteger estoure o limite do int.
        int idx = Math.abs(replicaIndex.getAndIncrement() % replicaUrls.size());
        String url = replicaUrls.get(idx);
        System.out.println("  [READ] Usando réplica: " + url);
        return DriverManager.getConnection(url, config.getReadUsername(), config.getReadPassword());
    }

    // Monta a URL de conexão JDBC do banco primário no formato exigido pelo MySQL Connector/J.
    private String buildJdbcUrl(String host, int port, String database) {
        return String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo",
                host, port, database
        );
    }
}
