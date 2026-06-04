package com.replicacao.db.repository;

import com.replicacao.db.connection.ConnectionManager;
import com.replicacao.db.model.Produto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProdutoRepository {

    private final ConnectionManager cm;

    public ProdutoRepository(ConnectionManager cm) {
        this.cm = cm;
    }

    /** INSERT no host primário. */
    public Produto inserir(Produto produto) throws SQLException {
        String sql = "INSERT INTO produto (descricao, categoria, valor, estoque, criado_por) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = cm.getWriteConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, produto.getDescricao());
            ps.setString(2, produto.getCategoria());
            ps.setBigDecimal(3, produto.getValor());
            ps.setInt(4, produto.getEstoque());
            ps.setString(5, produto.getCriadoPor());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) produto.setId(rs.getInt(1));
            }
        }
        return produto;
    }

    /** SELECT na réplica — retorna todos os produtos. */
    public List<Produto> listarTodos() throws SQLException {
        List<Produto> lista = new ArrayList<>();
        String sql = "SELECT id, descricao, categoria, valor, estoque, criado_em, criado_por FROM produto ORDER BY id";
        try (Connection conn = cm.getReadConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Produto p = mapRow(rs);
                lista.add(p);
            }
        }
        return lista;
    }

    /** SELECT na réplica — produtos com estoque abaixo do limite. */
    public List<Produto> listarBaixoEstoque(int limite) throws SQLException {
        List<Produto> lista = new ArrayList<>();
        String sql = "SELECT id, descricao, categoria, valor, estoque, criado_em, criado_por FROM produto WHERE estoque < ? ORDER BY estoque ASC";
        try (Connection conn = cm.getReadConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
            }
        }
        return lista;
    }

    private Produto mapRow(ResultSet rs) throws SQLException {
        Produto p = new Produto();
        p.setId(rs.getInt("id"));
        p.setDescricao(rs.getString("descricao"));
        p.setCategoria(rs.getString("categoria"));
        p.setValor(rs.getBigDecimal("valor"));
        p.setEstoque(rs.getInt("estoque"));
        p.setCriadoEm(rs.getTimestamp("criado_em").toLocalDateTime());
        p.setCriadoPor(rs.getString("criado_por"));
        return p;
    }
}
