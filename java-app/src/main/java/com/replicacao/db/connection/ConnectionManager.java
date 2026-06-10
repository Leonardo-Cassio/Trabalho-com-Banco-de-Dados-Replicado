package com.replicacao.db.connection;

import com.replicacao.db.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GERENCIADOR DE CONEXÕES — NÚCLEO DA SEPARAÇÃO LEITURA/ESCRITA
 *
 * Esta classe é responsável por garantir que:
 *   - Todo INSERT, UPDATE, DELETE vá para o host PRIMÁRIO (via getWriteConnection)
 *   - Todo SELECT vá para uma RÉPLICA (via getReadConnection)
 *
 * Os repositories chamam explicitamente um dos dois métodos:
 *   - ClienteRepository.inserir()       → getWriteConnection()
 *   - ClienteRepository.listarTodos()   → getReadConnection()
 *   - PedidoRepository.inserirComItens()→ getWriteConnection()
 *   - PedidoRepository.buscarPorId()    → getReadConnection()
 *   - (e assim por diante em todos os repositories)
 *
 * BALANCEAMENTO EM ROUND-ROBIN:
 *   Se houver múltiplas réplicas configuradas (ex: "IP1:3307,IP2:3307"),
 *   cada chamada a getReadConnection() usa a próxima réplica da lista,
 *   distribuindo a carga de leitura uniformemente entre elas.
 *   O AtomicInteger garante que o incremento seja seguro em ambiente concorrente.
 */
public class ConnectionManager {

    private final AppConfig config;

    // Lista de URLs JDBC das réplicas, montadas no construtor a partir do config.properties.
    // Exemplo: ["jdbc:mysql://127.0.0.1:3307/aula-db?...", "jdbc:mysql://IP2:3307/aula-db?..."]
    private final List<String> replicaUrls = new ArrayList<>();

    // Contador thread-safe para round-robin entre réplicas.
    // Incrementa a cada chamada a getReadConnection() e faz módulo pelo total de réplicas.
    private final AtomicInteger replicaIndex = new AtomicInteger(0);

    public ConnectionManager(AppConfig config) {
        this.config = config;

        // Monta as URLs JDBC para cada réplica listada no config.properties.
        // O formato no config é "host:porta" (ex: "127.0.0.1:3307").
        // Se a porta for omitida, usa 3306 como padrão.
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

    /**
     * Retorna uma nova conexão JDBC com o host PRIMÁRIO.
     *
     * Chamada por: inserir(), atualizarStatus(), deletar() em todos os repositories.
     * Toda operação de escrita (INSERT, UPDATE, DELETE) deve usar este método.
     * A conexão é aberta a cada chamada — feche-a com try-with-resources nos repositories.
     */
    public Connection getWriteConnection() throws SQLException {
        String url = buildJdbcUrl(config.getWriteHost(), config.getWritePort(), config.getWriteDatabase());
        return DriverManager.getConnection(url, config.getWriteUsername(), config.getWritePassword());
    }

    /**
     * Retorna uma nova conexão JDBC com a próxima RÉPLICA disponível (round-robin).
     *
     * Chamada por: listarTodos(), buscarPorId(), historicoPorCliente(), etc.
     * Toda operação de leitura (SELECT, JOIN, agregações) deve usar este método.
     *
     * Com 1 réplica: sempre conecta nela.
     * Com N réplicas: alterna entre elas — réplica 0, 1, 2, 0, 1, 2, ...
     */
    public Connection getReadConnection() throws SQLException {
        // Math.abs evita índice negativo caso o AtomicInteger estoure o limite do int.
        int idx = Math.abs(replicaIndex.getAndIncrement() % replicaUrls.size());
        String url = replicaUrls.get(idx);
        System.out.println("  [READ] Usando réplica: " + url);
        return DriverManager.getConnection(url, config.getReadUsername(), config.getReadPassword());
    }

    /**
     * Monta a URL de conexão JDBC no formato exigido pelo MySQL Connector/J.
     * Parâmetros incluídos:
     *   - useSSL=false               → desabilita SSL (não necessário em ambiente local/acadêmico)
     *   - allowPublicKeyRetrieval=true → necessário para autenticação com MySQL 8+
     *   - serverTimezone=America/Sao_Paulo → evita erros de conversão de DATETIME
     */
    private String buildJdbcUrl(String host, int port, String database) {
        return String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo",
                host, port, database
        );
    }
}
