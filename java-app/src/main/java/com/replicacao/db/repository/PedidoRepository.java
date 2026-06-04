package com.replicacao.db.repository;

import com.replicacao.db.connection.ConnectionManager;
import com.replicacao.db.model.Pedido;
import com.replicacao.db.model.PedidoItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PedidoRepository {

    private final ConnectionManager cm;

    public PedidoRepository(ConnectionManager cm) {
        this.cm = cm;
    }

    /**
     * Insere pedido e seus itens no host primário dentro de uma única transação.
     */
    public Pedido inserirComItens(Pedido pedido) throws SQLException {
        String sqlPedido = "INSERT INTO pedido (cliente_id, valor_total, status, criado_por) VALUES (?, ?, ?, ?)";
        String sqlItem   = "INSERT INTO pedido_item (pedido_id, produto_id, quantidade, valor_unitario) VALUES (?, ?, ?, ?)";

        try (Connection conn = cm.getWriteConnection()) {
            conn.setAutoCommit(false);
            try {
                // Insere pedido
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

                // Insere itens
                for (PedidoItem item : pedido.getItens()) {
                    item.setPedidoId(pedido.getId());
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

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
        return pedido;
    }

    /** SELECT na réplica — busca pedido por ID. */
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

    /** SELECT na réplica — busca itens de um pedido. */
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

    /** SELECT na réplica — últimos N pedidos de um cliente. */
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

    /** SELECT na réplica — relatório agregado de vendas. */
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

    /** SELECT na réplica — todos os pedidos de um cliente com seus itens. */
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
