package com.replicacao.db.repository;

import com.replicacao.db.connection.ConnectionManager;
import com.replicacao.db.model.Cliente;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositório de clientes — encapsula todos os SQLs da tabela "cliente".
 */
public class ClienteRepository {

    private final ConnectionManager cm;

    public ClienteRepository(ConnectionManager cm) {
        this.cm = cm;
    }

    /**
     * INSERT no host PRIMÁRIO.
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
        return Optional.empty();
    }

    /**
     * DELETE no host PRIMÁRIO — remove o cliente pelo ID.
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
