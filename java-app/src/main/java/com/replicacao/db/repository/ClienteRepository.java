package com.replicacao.db.repository;

import com.replicacao.db.connection.ConnectionManager;
import com.replicacao.db.model.Cliente;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REPOSITÓRIO DE CLIENTES
 *
 * Responsável por todas as operações de banco de dados relacionadas à tabela `cliente`.
 * Aplica a regra de separação leitura/escrita:
 *   - Escrita (INSERT, DELETE) → cm.getWriteConnection()  → host primário
 *   - Leitura (SELECT)         → cm.getReadConnection()   → réplica
 *
 * Chamado por: DataGeneratorService (para cadastrar, listar e deletar clientes)
 */
public class ClienteRepository {

    private final ConnectionManager cm;

    public ClienteRepository(ConnectionManager cm) {
        this.cm = cm;
    }

    /**
     * Insere um novo cliente no host PRIMÁRIO.
     * Chamado por DataGeneratorService.cadastrarClientes() na fase inicial.
     *
     * RETURN_GENERATED_KEYS faz o JDBC devolver o ID gerado pelo AUTO_INCREMENT,
     * que é salvo no objeto cliente para uso posterior.
     */
    public Cliente inserir(Cliente cliente) throws SQLException {
        String sql = "INSERT INTO cliente (nome, email, criado_por) VALUES (?, ?, ?)";
        try (Connection conn = cm.getWriteConnection();                                // [WRITE → Primário]
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, cliente.getNome());
            ps.setString(2, cliente.getEmail());
            ps.setString(3, cliente.getCriadoPor());
            ps.executeUpdate();

            // Recupera o ID gerado pelo banco e armazena no objeto
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) cliente.setId(rs.getInt(1));
            }
        }
        return cliente;
    }

    /**
     * Busca todos os clientes na RÉPLICA.
     * Chamado por DataGeneratorService.criarPedido() para escolher um cliente aleatório.
     *
     * Se a réplica ainda não sincronizou (lag), pode retornar lista vazia logo após
     * a inserção inicial — por isso Main.java espera 2 segundos antes do primeiro ciclo.
     */
    public List<Cliente> listarTodos() throws SQLException {
        List<Cliente> lista = new ArrayList<>();
        String sql = "SELECT id, nome, email, criado_em, criado_por FROM cliente ORDER BY id";
        try (Connection conn = cm.getReadConnection();                                 // [READ → Réplica]
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
     * Busca na RÉPLICA um cliente que NÃO tenha nenhum pedido associado.
     * Usado por DataGeneratorService.removerClienteAntigo() para encontrar
     * um candidato seguro para DELETE — evita erro de chave estrangeira (FK).
     *
     * O LEFT JOIN com a tabela `pedido` retorna NULL em pedido.id para clientes
     * sem pedidos. A cláusula WHERE p.id IS NULL filtra apenas esses casos.
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
        try (Connection conn = cm.getReadConnection();                                 // [READ → Réplica]
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
        return Optional.empty(); // todos os clientes têm pedidos — DELETE será pulado
    }

    /**
     * Deleta um cliente pelo ID no host PRIMÁRIO.
     * Chamado por DataGeneratorService.removerClienteAntigo() a cada 5 ciclos.
     * Só deve ser chamado após confirmar via buscarClienteSemPedidos() que
     * não há pedidos vinculados — caso contrário MySQL lança erro de FK.
     *
     * Retorna true se alguma linha foi removida, false caso o ID não exista.
     */
    public boolean deletar(int id) throws SQLException {
        String sql = "DELETE FROM cliente WHERE id = ?";
        try (Connection conn = cm.getWriteConnection();                                // [WRITE → Primário]
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Busca um cliente pelo ID na RÉPLICA.
     * Disponível para uso futuro ou consultas pontuais.
     */
    public Optional<Cliente> buscarPorId(int id) throws SQLException {
        String sql = "SELECT id, nome, email, criado_em, criado_por FROM cliente WHERE id = ?";
        try (Connection conn = cm.getReadConnection();                                 // [READ → Réplica]
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
