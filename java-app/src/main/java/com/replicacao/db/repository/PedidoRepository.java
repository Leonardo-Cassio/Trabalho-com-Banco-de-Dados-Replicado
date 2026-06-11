package com.replicacao.db.repository;

import com.replicacao.db.connection.ConnectionManager;
import com.replicacao.db.model.Pedido;
import com.replicacao.db.model.PedidoItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositório de pedidos — encapsula os SQLs das tabelas "pedido" e "pedido_item".
 *
 * SEPARAÇÃO LEITURA/ESCRITA:
 *   inserirComItens()  → PRIMÁRIO (INSERT com transação)
 *   atualizarStatus()  → PRIMÁRIO (UPDATE)
 *   buscarPorId()      → RÉPLICA  (SELECT + JOIN)
 *   buscarItens()      → RÉPLICA  (SELECT + JOIN)
 *   historico()        → RÉPLICA  (SELECT + ORDER BY + LIMIT)
 *   relatorioAgregado()→ RÉPLICA  (SELECT + COUNT + AVG + SUM)
 */
public class PedidoRepository {

    private final ConnectionManager cm;

    public PedidoRepository(ConnectionManager cm) {
        this.cm = cm;
    }

    /**
     * INSERT no PRIMÁRIO — insere o pedido e todos os seus itens em uma única transação.
     *
     * POR QUE UMA TRANSAÇÃO?
     *   Se o INSERT do pedido funcionar mas o de algum item falhar, a transação
     *   é revertida (rollback) e o banco fica em estado consistente.
     *   Sem transação, poderíamos ter um pedido sem itens no banco.
     *
     * RETURN_GENERATED_KEYS:
     *   Pede ao driver que retorne o ID gerado pelo auto_increment após o INSERT.
     *   Necessário para associar os itens ao pedido recém-criado (pedido_item.pedido_id).
     */
    public Pedido inserirComItens(Pedido pedido) throws SQLException {
        String sqlPedido = "INSERT INTO pedido (cliente_id, valor_total, status, criado_por) VALUES (?, ?, ?, ?)";
        String sqlItem   = "INSERT INTO pedido_item (pedido_id, produto_id, quantidade, valor_unitario) VALUES (?, ?, ?, ?)";

        try (Connection conn = cm.getWriteConnection()) {
            // Desativa o auto-commit para abrir a transação manualmente
            conn.setAutoCommit(false);
            try {
                // Passo 1: insere o pedido e obtém o ID gerado
                try (PreparedStatement ps = conn.prepareStatement(sqlPedido, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, pedido.getClienteId());
                    ps.setBigDecimal(2, pedido.getValorTotal());
                    ps.setString(3, pedido.getStatus());
                    ps.setString(4, pedido.getCriadoPor());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) pedido.setId(rs.getInt(1));
                    }
                }

                // Passo 2: insere cada item associado ao pedido recém-criado
                for (PedidoItem item : pedido.getItens()) {
                    item.setPedidoId(pedido.getId()); // vincula o item ao pedido
                    try (PreparedStatement ps = conn.prepareStatement(sqlItem, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, item.getPedidoId());
                        ps.setInt(2, item.getProdutoId());
                        ps.setInt(3, item.getQuantidade());
                        ps.setBigDecimal(4, item.getValorUnitario());
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs.next()) item.setId(rs.getInt(1));
                        }
                    }
                }

                // Confirma a transação — tudo inserido com sucesso
                conn.commit();
            } catch (SQLException e) {
                // Qualquer erro reverte o pedido e todos os itens já inseridos
                conn.rollback();
                throw e;
            }
        }
        return pedido;
    }

    /**
     * UPDATE no PRIMÁRIO — altera o status de um pedido pelo ID.
     *
     * Demonstra que UPDATE, assim como INSERT e DELETE,
     * vai exclusivamente ao host primário.
     *
     * Retorna true se alguma linha foi afetada (pedido existia),
     * false se o ID não foi encontrado.
     */
    public boolean atualizarStatus(int pedidoId, String novoStatus) throws SQLException {
        String sql = "UPDATE pedido SET status = ? WHERE id = ?";
        try (Connection conn = cm.getWriteConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, novoStatus);
            ps.setInt(2, pedidoId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * SELECT na RÉPLICA — busca pedido por ID com JOIN no cliente.
     *
     * Retorna Optional.empty() se o ID não existir — evita NullPointerException
     * no código chamador, que decide o que exibir quando não há resultado.
     */
    public Optional<Pedido> buscarPorId(int id) throws SQLException {
        String sql = """
                SELECT p.id, p.cliente_id, c.nome AS cliente_nome,
                       p.valor_total, p.status, p.criado_em, p.criado_por
                FROM pedido p
                JOIN cliente c ON c.id = p.cliente_id
                WHERE p.id = ?
                """;
        try (Connection conn = cm.getReadConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Pedido p = new Pedido();
                    p.setId(rs.getInt("id"));
                    p.setClienteId(rs.getInt("cliente_id"));
                    p.setValorTotal(rs.getBigDecimal("valor_total"));
                    p.setStatus(rs.getString("status"));
                    p.setCriadoEm(rs.getTimestamp("criado_em").toLocalDateTime());
                    p.setCriadoPor(rs.getString("criado_por"));
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * SELECT na RÉPLICA — busca todos os itens de um pedido com JOIN no produto.
     * Retorna lista vazia se o pedido não tiver itens.
     */
    public List<PedidoItem> buscarItensDoPedido(int pedidoId) throws SQLException {
        List<PedidoItem> itens = new ArrayList<>();
        String sql = """
                SELECT pi.id, pi.pedido_id, pi.produto_id, pr.descricao AS produto_descricao,
                       pi.quantidade, pi.valor_unitario
                FROM pedido_item pi
                JOIN produto pr ON pr.id = pi.produto_id
                WHERE pi.pedido_id = ?
                """;
        try (Connection conn = cm.getReadConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, pedidoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PedidoItem item = new PedidoItem();
                    item.setId(rs.getInt("id"));
                    item.setPedidoId(rs.getInt("pedido_id"));
                    item.setProdutoId(rs.getInt("produto_id"));
                    item.setQuantidade(rs.getInt("quantidade"));
                    item.setValorUnitario(rs.getBigDecimal("valor_unitario"));
                    itens.add(item);
                }
            }
        }
        return itens;
    }

    /**
     * SELECT na RÉPLICA — retorna os últimos N pedidos de um cliente.
     * ORDER BY id DESC garante os mais recentes primeiro;
     * LIMIT restringe ao histórico solicitado (normalmente 5).
     */
    public List<Pedido> historicoPorCliente(int clienteId, int limite) throws SQLException {
        List<Pedido> lista = new ArrayList<>();
        String sql = """
                SELECT id, cliente_id, valor_total, status, criado_em, criado_por
                FROM pedido
                WHERE cliente_id = ?
                ORDER BY id DESC
                LIMIT ?
                """;
        try (Connection conn = cm.getReadConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, clienteId);
            ps.setInt(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Pedido p = new Pedido();
                    p.setId(rs.getInt("id"));
                    p.setClienteId(rs.getInt("cliente_id"));
                    p.setValorTotal(rs.getBigDecimal("valor_total"));
                    p.setStatus(rs.getString("status"));
                    p.setCriadoEm(rs.getTimestamp("criado_em").toLocalDateTime());
                    p.setCriadoPor(rs.getString("criado_por"));
                    lista.add(p);
                }
            }
        }
        return lista;
    }

    /**
     * SELECT na RÉPLICA — relatório agregado com COUNT, AVG e SUM.
     * Demonstra que funções de agregação também vão à réplica.
     */
    public void exibirRelatorioAgregado() throws SQLException {
        String sql = """
                SELECT
                    COUNT(*)           AS total_pedidos,
                    AVG(valor_total)   AS media_valor,
                    SUM(valor_total)   AS total_vendido
                FROM pedido
                """;
        try (Connection conn = cm.getReadConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                System.out.printf("  Total de pedidos : %d%n",        rs.getInt("total_pedidos"));
                System.out.printf("  Valor médio      : R$ %.2f%n",   rs.getDouble("media_valor"));
                System.out.printf("  Total vendido    : R$ %.2f%n",   rs.getDouble("total_vendido"));
            }
        }
    }

    /**
     * SELECT na RÉPLICA — todos os pedidos de um cliente (usado pela API REST).
     * Sem LIMIT para retornar o histórico completo.
     */
    public List<Pedido> buscarPorClienteId(int clienteId) throws SQLException {
        List<Pedido> lista = new ArrayList<>();
        String sql = """
                SELECT id, cliente_id, valor_total, status, criado_em, criado_por
                FROM pedido
                WHERE cliente_id = ?
                ORDER BY id DESC
                """;
        try (Connection conn = cm.getReadConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Pedido p = new Pedido();
                    p.setId(rs.getInt("id"));
                    p.setClienteId(rs.getInt("cliente_id"));
                    p.setValorTotal(rs.getBigDecimal("valor_total"));
                    p.setStatus(rs.getString("status"));
                    p.setCriadoEm(rs.getTimestamp("criado_em").toLocalDateTime());
                    p.setCriadoPor(rs.getString("criado_por"));
                    lista.add(p);
                }
            }
        }
        return lista;
    }
}
