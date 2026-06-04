-- Executado automaticamente no primário na primeira vez que o container sobe

-- Cria o banco
CREATE DATABASE IF NOT EXISTS `aula-db`;

-- Cria usuário de replicação que a réplica vai usar para se conectar ao primário
CREATE USER IF NOT EXISTS 'replicator'@'%' IDENTIFIED WITH mysql_native_password BY 'replicator_pass';
GRANT REPLICATION SLAVE ON *.* TO 'replicator'@'%';
FLUSH PRIVILEGES;

-- Cria as tabelas
USE `aula-db`;

CREATE TABLE IF NOT EXISTS cliente (
    id         INT AUTO_INCREMENT,
    nome       VARCHAR(80)  NOT NULL,
    email      VARCHAR(120) NOT NULL,
    criado_em  DATETIME     DEFAULT NOW(),
    criado_por VARCHAR(50)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS produto (
    id         INT AUTO_INCREMENT,
    descricao  VARCHAR(80)    NOT NULL,
    categoria  VARCHAR(30)    NOT NULL,
    valor      NUMERIC(15, 2) NOT NULL,
    estoque    INT            NOT NULL,
    criado_em  DATETIME       DEFAULT NOW(),
    criado_por VARCHAR(50)    NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS pedido (
    id          INT AUTO_INCREMENT,
    cliente_id  INT            NOT NULL,
    valor_total NUMERIC(15, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    criado_em   DATETIME       DEFAULT NOW(),
    criado_por  VARCHAR(50)    NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (cliente_id) REFERENCES cliente (id)
);

CREATE TABLE IF NOT EXISTS pedido_item (
    id             INT AUTO_INCREMENT,
    pedido_id      INT            NOT NULL,
    produto_id     INT            NOT NULL,
    quantidade     INT            NOT NULL,
    valor_unitario NUMERIC(15, 2) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (pedido_id)  REFERENCES pedido (id),
    FOREIGN KEY (produto_id) REFERENCES produto (id)
);
