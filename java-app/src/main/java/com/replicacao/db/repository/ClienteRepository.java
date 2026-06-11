package com.replicacao.db.repository;

import com.replicacao.db.connection.ConnectionManager;
import com.replicacao.db.model.Cliente;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositório de clientes — encapsula todos os SQLs da tabela "cliente".
 *
 * REGRA DE OURO:
 *   Métodos que MODIFICAM dados → cm.getWriteConnection() → vai ao PRIMÁRIO
 *   Métodos que LEEM dados      → cm.getReadConnection()  → vai à RÉPLICA
 *
 * Essa separação é feita por convenção: INSERT/UPDATE/DELETE usam write,
 * SELECT usa read. O ConnectionManager não impede o uso errado — é
 * responsabilidade do desenvolvedor chamar o método correto.
 */
public class ClienteRepository {

    private final ConnectionManager cm;

    public ClienteRepository(ConnectionManager cm) {
        this.cm = cm;
    }

    /**
     * INSERT no host PRIMÁRIO.
     * Abre a conexão de escrita, executa o INSERT e captura o ID gerado
     * pelo auto_increment do MySQL via getGeneratedKeys().
     *
     * try-with-resources garante que a conexão e o statement são fechados
     * mesmo se ocorrer uma exceção — evita vazamento de conexões.
     */
    public Cliente inserir(Cliente cliente) throws SQLException {
        String sql = "INSERT INTO cliente (nome, email, criado_por) VALUES (?, ?, ?)";
        try (Connection conn = cm.getWriteConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, cliente.getNome());
            ps.setString(2, cliente.getEmail());
            ps.setString(3, cliente.getCriadoPor());
            ps.executeUpdate();

            // Recupera o ID gerado pelo banco após o INSERT
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) cliente.setId(rs.getInt(1));
            }
        }
        return cliente;
    }

    /**
     * SELECT na RÉPLICA — retorna todos os clientes ordenados por ID.
     * Usado pelo DataGeneratorService para escolher um cliente aleatório
     * na hora de criar um pedido.
     */
    public List<Cliente> listarTodos() throws SQLException {
        List<Cliente> lista = new ArrayList<>();
        String sql = "SELECT id, nome, email, criado_em, criado_por FROM cliente ORDER BY id";
        try (Connection conn = cm.getReadConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Cliente c = new Cliente();
                c.setId(rs.getInt("id"));
                c.setNome(rs.getString("nome"));
                c.setEmail(rs.getString("email"));
                c.setCriadoEm(rs.getTimestamp("criado_em").toLocalDateTime());
                c.setCriadoPor(rs.getString("criado_por"));
                lista.add(c);
            }
        }
        return lista;
    }

    /**
     * SELECT na RÉPLICA — retorna o primeiro cliente que NÃO tem nenhum pedido.
     *
     * O LEFT JOIN com a tabela pedido traz todos os clientes; o WHERE p.id IS NULL
     * filtra apenas os que não têm nenhum registro na tabela pedido.
     * Isso garante que o DELETE subsequente não vai violar a foreign key
     * (pedido.cliente_id → cliente.id).
     *
     * Retorna Optional.empty() se todos os clientes já tiverem pedidos.
     */
    public Optional<Cliente> buscarClienteSemPedidos() throws SQLException {
        String sql = """
                SELECT c.id, c.nome, c.email, c.criado_em, c.criado_por
                FROM cliente c
                LEFT JOIN pedido p ON p.cliente_id = c.id
                WHERE p.id IS NULL
                ORDER BY c.id ASC
                LIMIT 1
                """;
        try (Connection conn = cm.getReadConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                Cliente c = new Cliente();
                c.setId(rs.getInt("id"));
                c.setNome(rs.getString("nome"));
                c.setEmail(rs.getString("email"));
                c.setCriadoEm(rs.getTimestamp("criado_em").toLocalDateTime());
                c.setCriadoPor(rs.getString("criado_por"));
                return Optional.of(c);
            }
        }
        // Retorna vazio se nenhum cliente sem pedidos foi encontrado.
        // O chamador (DataGeneratorService) decide o que fazer nesse caso.
        return Optional.empty();
    }

    /**
     * DELETE no host PRIMÁRIO — remove o cliente pelo ID.
     *
     * IMPORTANTE: só deve ser chamado para clientes sem pedidos,
     * pois pedido tem foreign key para cliente. Chamar para um cliente
     * com pedidos causa SQLException por violação de integridade referencial.
     *
     * Retorna true se a linha foi removida, false se o ID não existia.
     */
    public boolean deletar(int id) throws SQLException {
        String sql = "DELETE FROM cliente WHERE id = ?";
        try (Connection conn = cm.getWriteConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** SELECT na RÉPLICA — retorna um cliente pelo ID. */
    public Optional<Cliente> buscarPorId(int id) throws SQLException {
        String sql = "SELECT id, nome, email, criado_em, criado_por FROM cliente WHERE id = ?";
        try (Connection conn = cm.getReadConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Cliente c = new Cliente();
                    c.setId(rs.getInt("id"));
                    c.setNome(rs.getString("nome"));
                    c.setEmail(rs.getString("email"));
                    c.setCriadoEm(rs.getTimestamp("criado_em").toLocalDateTime());
                    c.setCriadoPor(rs.getString("criado_por"));
                    return Optional.of(c);
                }
            }
        }
        return Optional.empty();
    }
}
