package com.replicacao.db.model;

import java.time.LocalDateTime;

public class Cliente {
    private int id;
    private String nome;
    private String email;
    private LocalDateTime criadoEm;
    private String criadoPor;

    public Cliente() {}

    public Cliente(String nome, String email, String criadoPor) {
        this.nome = nome;
        this.email = email;
        this.criadoPor = criadoPor;
    }

    public int getId()                  { return id; }
    public void setId(int id)           { this.id = id; }
    public String getNome()             { return nome; }
    public void setNome(String nome)    { this.nome = nome; }
    public String getEmail()            { return email; }
    public void setEmail(String email)  { this.email = email; }
    public LocalDateTime getCriadoEm()  { return criadoEm; }
    public void setCriadoEm(LocalDateTime c) { this.criadoEm = c; }
    public String getCriadoPor()        { return criadoPor; }
    public void setCriadoPor(String c)  { this.criadoPor = c; }
}
