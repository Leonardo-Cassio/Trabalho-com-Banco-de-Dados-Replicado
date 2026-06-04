package com.replicacao.db.model;

import java.math.BigDecimal;

public class PedidoItem {
    private int id;
    private int pedidoId;
    private int produtoId;
    private int quantidade;
    private BigDecimal valorUnitario;

    public PedidoItem() {}

    public PedidoItem(int pedidoId, int produtoId, int quantidade, BigDecimal valorUnitario) {
        this.pedidoId = pedidoId;
        this.produtoId = produtoId;
        this.quantidade = quantidade;
        this.valorUnitario = valorUnitario;
    }

    public int getId()                          { return id; }
    public void setId(int id)                   { this.id = id; }
    public int getPedidoId()                    { return pedidoId; }
    public void setPedidoId(int p)              { this.pedidoId = p; }
    public int getProdutoId()                   { return produtoId; }
    public void setProdutoId(int p)             { this.produtoId = p; }
    public int getQuantidade()                  { return quantidade; }
    public void setQuantidade(int q)            { this.quantidade = q; }
    public BigDecimal getValorUnitario()        { return valorUnitario; }
    public void setValorUnitario(BigDecimal v)  { this.valorUnitario = v; }
}
