package com.replicacao.db.repository;

import com.replicacao.db.connection.ConnectionManager;
import com.replicacao.db.model.Pedido;
import com.replicacao.db.model.PedidoItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REPOSITÓRIO DE PEDIDOS
 *
 * Responsável por todas as operações de banco de dados das tabelas `pedido` e `pedido_item`.
 * Aplica a regra de separação leitura/escrita:
 *   - Escrita (INSERT, UPDATE) → cm.getWriteConnection()  → host primário
 *   - Leitura (SELECT)         → cm.getReadConnection()   → réplica
 *
 * Chamado por: DataGeneratorService (criar pedido, atualizar status, consultas)
 *              API REST via routes/pedidos.js e routes/clientes.js (somente leitura)
 */
public class PedidoRepository {

    private final ConnectionManager cm;

    public PedidoRepository(ConnectionManager cm) {
        this.cm = cm;
    }

    /**
     * Insere um pedido e todos os seus itens no host PRIMÁRIO em uma única transação.
     * Chamado por DataGeneratorService.criarPedido() a cada ciclo.
     *
     * Por que transação única?
     *   Garante que pedido e itens sejam gravados juntos — se a inserção de qualquer
     *   item falhar, o pedido inteiro é desfeito (rollback), evitando dados inconsistentes.
     *
     * Fluxo:
     *   1. Desabilita autocommit (conn.setAutoCommit(false))
     *   2. INSERT INTO pedido → captura o ID gerado
     *   3. Para cada item: INSERT INTO pedido_item com o pedido_id recém-gerado
     *   4. conn.commit() confirma tudo de uma vez
     *   5. Em caso de erro: conn.rollback() desfaz tudo
     */
    public Pedido inserirComItens(Pedido pedido) throws SQLException {
        String sqlPedido = "INSERT INTO pedido (cliente_id, valor_total, status, criado_por) VALUES (?, ?, ?, ?)";
        String sqlItem   = "INSERT INTO pedido_item (pedido_id, produto_id, quantidade, valor_unitario) VALUES (?, ?, ?, ?)";

        try (Connection conn = cm.getWriteConnection()) {                              // [WRITE → Primário]
            conn.setAutoCommit(false);
            try {
                // Insere o pedido e recupera o ID gerado pelo AUTO_INCREMENT
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

                // Insere cada item associando ao ID do pedido recém-criado
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

                conn.commit(); // confirma pedido + todos os itens de uma vez
            } catch (SQLException e) {
                conn.rollback(); // erro em qualquer item → desfaz o pedido inteiro
                throw e;
            }
        }
        return pedido;
    }

    /**
     * Busca um pedido por ID na RÉPLICA, fazendo JOIN com a tabela cliente.
     * Chamado por DataGeneratorService.executarConsultas() e pela API REST (GET /pedidos/:id).
     *
     * O JOIN traz nome e e-mail do cliente sem precisar de uma segunda consulta.
     * Retorna Optional.empty() se o ID não existir.
     */
    public Optional<Pedido> buscarPorId(int id) throws SQLException {
        String sql = """
                SELECT p.id, p.cliente_id, c.nome AS cliente_nome,
                       p.valor_total, p.status, p.criado_em, p.criado_por
                FROM pedido p
                JOIN cliente c ON c.id = p.cliente_id
                WHERE p.id = ?
                """;
        try (Connection conn = cm.getReadConnection();                                 // [READ → Réplica]
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
     * Busca os itens de um pedido na RÉPLICA, fazendo JOIN com a tabela produto.
     * Chamado por DataGeneratorService.executarConsultas() logo após criarPedido().
     *
     * O JOIN com `produto` traz a descrição do produto sem consulta adicional.
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
        try (Connection conn = cm.getReadConnection();                                 // [READ → Réplica]
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
     * Busca os últimos N pedidos de um cliente na RÉPLICA.
     * Chamado por DataGeneratorService.executarConsultas() para mostrar histórico.
     * O parâmetro `limite` controla quantos pedidos são retornados (ex: 5).
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
        try (Connection conn = cm.getReadConnection();                                 // [READ → Réplica]
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
     * Executa um relatório agregado na RÉPLICA e imprime no console.
     * Chamado por DataGeneratorService.executarConsultas() ao final de cada ciclo.
     *
     * Demonstra o uso de funções de agregação (COUNT, AVG, SUM) — operações
     * típicas de relatórios que devem ir à réplica para não sobrecarregar o primário.
     */
    public void exibirRelatorioAgregado() throws SQLException {
        String sql = """
                SELECT
                    COUNT(*)           AS total_pedidos,
                    AVG(valor_total)   AS media_valor,
                    SUM(valor_total)   AS total_vendido
                FROM pedido
                """;
        try (Connection conn = cm.getReadConnection();                                 // [READ → Réplica]
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
     * Atualiza o status de um pedido no host PRIMÁRIO.
     * Chamado por DataGeneratorService.atualizarStatusPedido() a cada ciclo,
     * logo após a criação do pedido — demonstra o UPDATE com replicação.
     *
     * Retorna true se alguma linha foi afetada (pedido encontrado e atualizado).
     */
    public boolean atualizarStatus(int pedidoId, String novoStatus) throws SQLException {
        String sql = "UPDATE pedido SET status = ? WHERE id = ?";
        try (Connection conn = cm.getWriteConnection();                                // [WRITE → Primário]
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, novoStatus);
            ps.setInt(2, pedidoId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Busca todos os pedidos de um cliente na RÉPLICA, ordenados do mais recente ao mais antigo.
     * Chamado pela API REST (GET /clientes/:id/pedidos) via routes/clientes.js.
     */
    public List<Pedido> buscarPorClienteId(int clienteId) throws SQLException {
        List<Pedido> lista = new ArrayList<>();
        String sql = """
                SELECT id, cliente_id, valor_total, status, criado_em, criado_por
                FROM pedido
                WHERE cliente_id = ?
                ORDER BY id DESC
                """;
        try (Connection conn = cm.getReadConnection();                                 // [READ → Réplica]
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
