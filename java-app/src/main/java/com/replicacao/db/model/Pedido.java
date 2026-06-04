package com.replicacao.db.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Pedido {
    private int id;
    private int clienteId;
    private BigDecimal valorTotal;
    private String status;
    private LocalDateTime criadoEm;
    private String criadoPor;
    private List<PedidoItem> itens = new ArrayList<>();

    public Pedido() {}

    public Pedido(int clienteId, BigDecimal valorTotal, String status, String criadoPor) {
        this.clienteId = clienteId;
        this.valorTotal = valorTotal;
        this.status = status;
        this.criadoPor = criadoPor;
    }

    public int getId()                        { return id; }
    public void setId(int id)                 { this.id = id; }
    public int getClienteId()                 { return clienteId; }
    public void setClienteId(int c)           { this.clienteId = c; }
    public BigDecimal getValorTotal()         { return valorTotal; }
    public void setValorTotal(BigDecimal v)   { this.valorTotal = v; }
    public String getStatus()                 { return status; }
    public void setStatus(String s)           { this.status = s; }
    public LocalDateTime getCriadoEm()        { return criadoEm; }
    public void setCriadoEm(LocalDateTime c)  { this.criadoEm = c; }
    public String getCriadoPor()              { return criadoPor; }
    public void setCriadoPor(String c)        { this.criadoPor = c; }
    public List<PedidoItem> getItens()        { return itens; }
    public void setItens(List<PedidoItem> i)  { this.itens = i; }
}
