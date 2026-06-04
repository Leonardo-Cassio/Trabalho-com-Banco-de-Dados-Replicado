package com.replicacao.db.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Produto {
    private int id;
    private String descricao;
    private String categoria;
    private BigDecimal valor;
    private int estoque;
    private LocalDateTime criadoEm;
    private String criadoPor;

    public Produto() {}

    public Produto(String descricao, String categoria, BigDecimal valor, int estoque, String criadoPor) {
        this.descricao = descricao;
        this.categoria = categoria;
        this.valor = valor;
        this.estoque = estoque;
        this.criadoPor = criadoPor;
    }

    public int getId()                        { return id; }
    public void setId(int id)                 { this.id = id; }
    public String getDescricao()              { return descricao; }
    public void setDescricao(String d)        { this.descricao = d; }
    public String getCategoria()              { return categoria; }
    public void setCategoria(String c)        { this.categoria = c; }
    public BigDecimal getValor()              { return valor; }
    public void setValor(BigDecimal v)        { this.valor = v; }
    public int getEstoque()                   { return estoque; }
    public void setEstoque(int e)             { this.estoque = e; }
    public LocalDateTime getCriadoEm()        { return criadoEm; }
    public void setCriadoEm(LocalDateTime c)  { this.criadoEm = c; }
    public String getCriadoPor()              { return criadoPor; }
    public void setCriadoPor(String c)        { this.criadoPor = c; }
}
