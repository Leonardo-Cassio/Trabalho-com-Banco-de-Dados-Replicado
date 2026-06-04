# Replicação de Banco de Dados com Separação de Leitura e Escrita

Projeto desenvolvido por **Gabriel Fillip** e **Leonardo Cassio**.

Projeto acadêmico que demonstra, na prática, uma arquitetura de banco de dados MySQL com replicação primário–réplica, onde toda escrita é direcionada ao nó primário e toda leitura é distribuída entre as réplicas disponíveis.

---

## Sumário

- [Visão Geral](#visão-geral)
- [Arquitetura](#arquitetura)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Banco de Dados](#banco-de-dados)
- [Aplicação Java](#aplicação-java)
- [API REST (Node.js)](#api-rest-nodejs)
- [Teste Local com Docker](#teste-local-com-docker)
- [Como Executar](#como-executar)
- [Como Testar](#como-testar)
- [Fluxo de Ciclos](#fluxo-de-ciclos)
- [Escalabilidade Horizontal](#escalabilidade-horizontal)

---

## Visão Geral

O sistema é composto por dois componentes principais:

| Componente | Linguagem | Responsabilidade |
|---|---|---|
| **java-app** | Java 17 + JDBC | Gera dados continuamente e demonstra separação read/write |
| **api-rest** | Node.js + Express | API REST que consulta exclusivamente as réplicas de leitura |

Ambos os componentes se conectam ao mesmo banco MySQL (`aula-db`), porém através de conexões distintas:

- **Host Primário (Write):** recebe `INSERT`, `UPDATE` e `DELETE`.
- **Réplicas (Read):** recebem todos os `SELECT`, `JOIN` e funções de agregação.

---

## Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                        Aplicação Java                        │
│                                                              │
│  ┌───────────────┐        ┌──────────────────────────────┐  │
│  │  Escrita      │──────► │  MySQL PRIMARY (Write)        │  │
│  │  (INSERT)     │        │  INSERT / UPDATE / DELETE     │  │
│  └───────────────┘        └──────────┬───────────────────┘  │
│                                      │ Replicação            │
│  ┌───────────────┐        ┌──────────▼───────────────────┐  │
│  │  Leitura      │◄────── │  MySQL REPLICA (Read)         │  │
│  │  (SELECT)     │        │  SELECT / JOIN / Agregações   │  │
│  └───────────────┘        └──────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        API REST (Node.js)                    │
│                                                              │
│  GET /pedidos/:id          ┌──────────────────────────────┐ │
│  GET /clientes/:id/pedidos │  MySQL REPLICA 1 (Read)      │ │
│  GET /produtos/baixo-estoque──► Round-Robin               │ │
│  GET /relatorios/vendas    │  MySQL REPLICA 2 (Read)      │ │
│                            │  MySQL REPLICA N (Read)      │ │
│                            └──────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## Estrutura do Projeto

```
Trabalho-com-Banco-de-Dados-Replicado/
├── sql/
│   └── schema.sql                        # DDL do banco de dados
│
├── java-app/                             # Aplicação Java
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/replicacao/db/
│       │   ├── Main.java                 # Ponto de entrada
│       │   ├── config/
│       │   │   └── AppConfig.java        # Leitura do config.properties
│       │   ├── connection/
│       │   │   └── ConnectionManager.java # Gerencia conexões write/read
│       │   ├── model/
│       │   │   ├── Cliente.java
│       │   │   ├── Produto.java
│       │   │   ├── Pedido.java
│       │   │   └── PedidoItem.java
│       │   ├── repository/
│       │   │   ├── ClienteRepository.java
│       │   │   ├── ProdutoRepository.java
│       │   │   └── PedidoRepository.java
│       │   └── service/
│       │       └── DataGeneratorService.java
│       └── resources/
│           └── config.properties         # Configuração de hosts e ciclos
│
└── api-rest/                             # API REST em Node.js
    ├── package.json
    ├── .env.example                      # Template de variáveis de ambiente
    └── src/
        ├── server.js                     # Bootstrap do servidor Express
        ├── database/
        │   └── replicaPool.js            # Gerencia pools de réplicas (round-robin)
        └── routes/
            ├── pedidos.js
            ├── clientes.js
            ├── produtos.js
            └── relatorios.js
```

---

## Banco de Dados

### Schema

O banco `aula-db` possui quatro tabelas relacionadas:

```
cliente (1) ──────────< pedido (1) ──────────< pedido_item >──────────(1) produto
```

Execute o arquivo `sql/schema.sql` no MySQL para criar todas as tabelas:

```bash
mysql -u root -p < sql/schema.sql
```

### Diagrama de Relacionamento

| Tabela | PK | FK |
|---|---|---|
| `cliente` | `id` | — |
| `produto` | `id` | — |
| `pedido` | `id` | `cliente_id → cliente.id` |
| `pedido_item` | `id` | `pedido_id → pedido.id`, `produto_id → produto.id` |

---

## Aplicação Java

### Pré-requisitos

- Java 17+
- Maven 3.8+

### Configuração

Edite o arquivo `java-app/src/main/resources/config.properties`:

```properties
# Host primário (ESCRITA)
db.write.host=<IP_DO_PROFESSOR>
db.write.port=3306
db.write.database=aula-db
db.write.username=root
db.write.password=<SENHA>

# Réplicas de leitura (READ) — separe por vírgula para múltiplas
db.read.replicas=<IP_REPLICA_1>:3307,<IP_REPLICA_2>:3308
db.read.database=aula-db
db.read.username=root
db.read.password=<SENHA>

# Intervalo entre ciclos (ms) e quantidade (0 = infinito)
app.cycle.interval.ms=3000
app.cycles=0
```

### Compilar e Executar

```bash
cd java-app

# Compilar e empacotar
mvn clean package -q

# Executar (Linux/Mac)
java -cp "target/db-replicacao.jar:target/libs/*" com.replicacao.db.Main

# Executar (Windows — separador de classpath é ;)
java -cp "target/db-replicacao.jar;target/libs/*" com.replicacao.db.Main
```

---

## API REST (Node.js)

### Pré-requisitos

- Node.js 18+
- npm

### Configuração

```bash
cd api-rest

# Copie o template de configuração
cp .env.example .env
```

Edite o `.env` com os dados das réplicas:

```env
PORT=3000

# Múltiplas réplicas separadas por vírgula
DB_READ_REPLICAS=<IP_REPLICA_1>:3307,<IP_REPLICA_2>:3308
DB_DATABASE=aula-db
DB_USERNAME=root
DB_PASSWORD=<SENHA>

# Produtos com estoque abaixo deste valor serão retornados como "baixo estoque"
LOW_STOCK_THRESHOLD=10
```

### Instalar Dependências e Executar

```bash
cd api-rest
npm install
npm start
```

---

## Teste Local com Docker

Antes de apresentar ao professor, você pode testar tudo localmente simulando um ambiente real de replicação com Docker.

### Pré-requisitos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado e rodando

### Subir o ambiente completo (um comando)

```powershell
# No PowerShell, na raiz do projeto:
.\testar-local.ps1
```

O script vai:
1. Subir um **MySQL Primário** na porta `3306`
2. Subir uma **MySQL Réplica** na porta `3307`
3. Configurar a replicação automaticamente entre os dois
4. Fazer um teste de escrita no primário e leitura na réplica para confirmar que está funcionando
5. Exibir as instruções para rodar Java e Node.js

Após o script terminar, o `config.properties` e o `.env` já estão apontando para `localhost:3306` (escrita) e `localhost:3307` (leitura) — não precisa alterar nada.

### Comandos úteis durante os testes

```powershell
# Ver logs do primário em tempo real
docker logs -f mysql-primary

# Ver logs da réplica em tempo real
docker logs -f mysql-replica

# Verificar se a replicação está ativa e sem erros
docker exec mysql-replica mysql -uroot -proot -e "SHOW REPLICA STATUS\G"

# Acessar o MySQL primário manualmente
docker exec -it mysql-primary mysql -uroot -proot aula-db

# Acessar o MySQL réplica manualmente
docker exec -it mysql-replica mysql -uroot -proot aula-db

# Parar tudo (mantém os dados)
docker compose down

# Parar e apagar os dados (começa do zero)
docker compose down -v
```

### O que verificar para confirmar que a replicação está funcionando

No resultado do `SHOW REPLICA STATUS\G`, procure estas linhas:

```
Replica_IO_Running: Yes     ← deve ser "Yes"
Replica_SQL_Running: Yes    ← deve ser "Yes"
Seconds_Behind_Source: 0    ← deve ser 0 (ou muito baixo)
Last_Error:                 ← deve estar vazio
```

---

## Como Executar

### Passo a passo completo

**1. Criar o banco de dados:**
```bash
mysql -u root -p < sql/schema.sql
```

**2. Configurar a aplicação Java** — edite `java-app/src/main/resources/config.properties` com os hosts fornecidos pelo professor.

**3. Compilar e iniciar a aplicação Java** (gera dados continuamente):
```bash
cd java-app
mvn clean package -q
java -cp "target/db-replicacao.jar;target/libs/*" com.replicacao.db.Main
```

**4. Em outro terminal, configurar e iniciar a API REST:**
```bash
cd api-rest
cp .env.example .env
# edite o .env com os hosts corretos
npm install
npm start
```

---

## Como Testar

### Testando a API REST

Com a aplicação Java rodando e gerando dados, teste os endpoints com `curl` ou qualquer cliente HTTP (Insomnia, Postman, etc.):

**Buscar pedido por ID:**
```bash
curl http://localhost:3000/pedidos/1
```

**Buscar pedidos de um cliente:**
```bash
curl http://localhost:3000/clientes/1/pedidos
```

**Produtos com baixo estoque:**
```bash
curl http://localhost:3000/produtos/baixo-estoque
```

**Relatório de vendas:**
```bash
curl http://localhost:3000/relatorios/vendas
```

**Health check:**
```bash
curl http://localhost:3000/health
```

### Exemplo de resposta — `GET /relatorios/vendas`

```json
{
  "resumo_geral": {
    "total_pedidos": 42,
    "total_vendido": "87450.30",
    "media_por_pedido": "2082.15",
    "menor_pedido": "349.90",
    "maior_pedido": "9599.50"
  },
  "por_status": [
    { "status": "FINALIZADO", "quantidade": 15, "total_valor": "32100.00" },
    { "status": "APROVADO",   "quantidade": 12, "total_valor": "25400.00" }
  ],
  "top_5_produtos": [
    { "id": 2, "descricao": "Mouse Logitech MX Master", "total_vendido": 38, "receita_total": "13296.20" }
  ]
}
```

---

## Fluxo de Ciclos

A cada ciclo da aplicação Java ocorre o seguinte:

```
FASE INICIAL (executada uma vez ao subir)
├── [WRITE → Primário] INSERT 5 clientes
└── [WRITE → Primário] INSERT 10 produtos

CICLO N (repetido indefinidamente)
│
├── [READ → Réplica]  SELECT clientes existentes   ← escolha aleatória
├── [READ → Réplica]  SELECT produtos existentes   ← escolha aleatória
│
├── [WRITE → Primário]
│   ├── INSERT INTO pedido                         ← transação única
│   └── INSERT INTO pedido_item (1 a 3 itens)
│
└── [READ → Réplica]
    ├── 4.1 SELECT pedido por ID (JOIN cliente)
    ├── 4.2 SELECT itens do pedido (JOIN produto)
    ├── 4.3 SELECT últimos 5 pedidos do cliente (ORDER BY id DESC LIMIT 5)
    └── 4.4 SELECT COUNT(*) / AVG(valor_total) / SUM(valor_total)
```

O intervalo entre ciclos e a quantidade total são configuráveis em `config.properties` (`app.cycle.interval.ms` e `app.cycles`).

---

## Escalabilidade Horizontal

### Aplicação Java

Para adicionar réplicas, basta separar os endereços por vírgula em `config.properties`:

```properties
db.read.replicas=192.168.1.10:3307,192.168.1.11:3307,192.168.1.12:3307
```

A classe `ConnectionManager` usa round-robin atômico (`AtomicInteger`) para distribuir as conexões de leitura entre todas as réplicas listadas, sem nenhuma alteração de código.

### API REST

Da mesma forma, adicione réplicas no `.env`:

```env
DB_READ_REPLICAS=192.168.1.10:3307,192.168.1.11:3307,192.168.1.12:3307
```

A classe `ReplicaPool` mantém um pool de conexões independente para cada réplica e distribui as queries em round-robin. Cada requisição HTTP pode ser atendida por uma réplica diferente, garantindo balanceamento de carga horizontal para leituras.

---

## Endpoints da API REST

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/pedidos/:id` | Busca pedido por ID com dados do cliente e itens |
| `GET` | `/clientes/:id/pedidos` | Histórico completo de pedidos de um cliente |
| `GET` | `/produtos/baixo-estoque` | Produtos com estoque abaixo do limiar configurado |
| `GET` | `/relatorios/vendas` | Relatório com SUM, COUNT, AVG, top 5 produtos |
| `GET` | `/health` | Verificação de saúde da API |

---

## Dependências

### Java
- **MySQL Connector/J 8.3** — driver JDBC oficial do MySQL

### Node.js
- **express 4.x** — framework HTTP
- **mysql2 3.x** — driver MySQL com suporte a Promises e pool de conexões
- **dotenv 16.x** — carregamento de variáveis de ambiente a partir do arquivo `.env`
