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
- [Dashboard Visual](#dashboard-visual)
- [Teste Local com Docker](#teste-local-com-docker)
- [Como Executar na Apresentação](#como-executar-na-apresentação)
- [Como Testar](#como-testar)
- [Fluxo de Ciclos](#fluxo-de-ciclos)
- [Reiniciar os Dados do Zero](#reiniciar-os-dados-do-zero)
- [Escalabilidade Horizontal](#escalabilidade-horizontal)

---

## Visão Geral

O sistema é composto por dois componentes principais:

| Componente | Linguagem | Responsabilidade |
|---|---|---|
| **java-app** | Java 17 + JDBC | Gera dados continuamente — INSERT, UPDATE e DELETE no primário |
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
│  │  INSERT       │        │  INSERT / UPDATE / DELETE     │  │
│  │  UPDATE       │        └──────────┬───────────────────┘  │
│  │  DELETE       │                   │ Replicação            │
│  └───────────────┘        ┌──────────▼───────────────────┐  │
│  ┌───────────────┐        │  MySQL REPLICA (Read)         │  │
│  │  Leitura      │◄────── │  SELECT / JOIN / Agregações   │  │
│  │  (SELECT)     │        └──────────────────────────────┘  │
│  └───────────────┘                                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  API REST + Dashboard (Node.js)              │
│                                                              │
│  localhost:3000 (navegador)  ┌──────────────────────────┐  │
│  GET /pedidos/:id            │  MySQL REPLICA 1 (Read)  │  │
│  GET /clientes/:id/pedidos ──► Round-Robin              │  │
│  GET /produtos/baixo-estoque │  MySQL REPLICA 2 (Read)  │  │
│  GET /relatorios/vendas      │  MySQL REPLICA N (Read)  │  │
│                              └──────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## Estrutura do Projeto

```
Trabalho-com-Banco-de-Dados-Replicado/
├── .github/
│   └── copilot-instructions.md           # Contexto completo do projeto para IA
├── sql/
│   └── schema.sql                        # DDL das 4 tabelas
├── docker/
│   ├── init-primary.sql                  # Executado ao criar o container primário
│   └── setup-replica.sh                  # Configura replicação entre containers
├── docker-compose.yml                    # Sobe primário + réplica + setup
├── testar-local.ps1                      # Script para teste local completo
│
├── java-app/                             # Aplicação Java
│   ├── pom.xml
│   ├── src/main/
│   │   ├── java/com/replicacao/db/
│   │   │   ├── Main.java                 # Ponto de entrada e loop de ciclos
│   │   │   ├── config/AppConfig.java     # Lê config.properties (externo tem prioridade)
│   │   │   ├── connection/ConnectionManager.java  # Gerencia conexões write/read
│   │   │   ├── model/                    # Cliente, Produto, Pedido, PedidoItem
│   │   │   ├── repository/               # ClienteRepository, ProdutoRepository, PedidoRepository
│   │   │   └── service/DataGeneratorService.java  # Gera dados e executa consultas
│   │   └── resources/
│   │       └── config.properties         # Config embutida no JAR (fallback)
│   └── target/
│       ├── config.properties             # ← EDITE ESTE na apresentação (prioridade)
│       ├── db-replicacao.jar
│       └── libs/                         # Dependências (mysql-connector-j)
│
└── api-rest/                             # API REST em Node.js
    ├── package.json
    ├── .env.example                      # Template de variáveis de ambiente
    ├── .env                              # ← EDITE ESTE na apresentação
    └── src/
        ├── server.js                     # Bootstrap Express + serve o dashboard
        ├── database/replicaPool.js       # Pools de réplicas com round-robin
        ├── routes/
        │   ├── pedidos.js
        │   ├── clientes.js
        │   ├── produtos.js
        │   └── relatorios.js
        └── public/
            └── index.html                # Dashboard visual (localhost:3000)
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

> **Atenção:** o campo `criado_por` é `VARCHAR(50)` em todas as tabelas. O valor inserido é `"Gabriel Fillip e Leonardo Cassio"` (32 caracteres).

---

## Aplicação Java

### Pré-requisitos

- Java 17+
- Maven 3.8+

### Configuração dos hosts

Existem três locais para o `config.properties`, em ordem de prioridade:

| Prioridade | Arquivo | Quando usar |
|---|---|---|
| 1ª | `java-app/target/config.properties` | **Apresentação** — edite aqui sem recompilar |
| 2ª | `java-app/target/classes/config.properties` | Gerado pelo Maven automaticamente |
| 3ª | Embutido no JAR | Fallback se nenhum externo existir |

**Na apresentação, edite apenas `java-app/target/config.properties`:**

```properties
db.write.host=IP_DO_PROFESSOR
db.write.port=3306
db.write.database=aula-db
db.write.username=root
db.write.password=SENHA

db.read.replicas=IP_REPLICA:3307
db.read.database=aula-db
db.read.username=root
db.read.password=SENHA

app.cycle.interval.ms=3000
app.cycles=0
```

### Compilar e Executar

```powershell
# Compilar (só necessário uma vez ou ao alterar o código)
cd java-app
mvn clean package -q

# Executar a partir de target/ (usa o config.properties externo)
cd target
java -cp "db-replicacao.jar;libs/*" com.replicacao.db.Main
```

---

## API REST (Node.js)

### Pré-requisitos

- Node.js 18+
- npm

### Configuração

Edite `api-rest/.env`:

```env
PORT=3000
DB_READ_REPLICAS=IP_REPLICA:3307
DB_DATABASE=aula-db
DB_USERNAME=root
DB_PASSWORD=SENHA
LOW_STOCK_THRESHOLD=10
```

### Executar

```powershell
cd api-rest
npm install   # apenas na primeira vez
npm start
```

---

## Dashboard Visual

Acesse `http://localhost:3000` no navegador após iniciar a API REST.

| Seção | Endpoint | Descrição |
|---|---|---|
| Buscar Pedido por ID | `GET /pedidos/:id` | Mostra pedido com cliente e itens |
| Pedidos do Cliente | `GET /clientes/:id/pedidos` | Histórico completo do cliente |
| Baixo Estoque | `GET /produtos/baixo-estoque` | Produtos críticos em vermelho |
| Relatório de Vendas | `GET /relatorios/vendas` | KPIs + auto-refresh configurável |

O botão **Auto-refresh** do relatório fica verde quando ativo e mostra o horário da última atualização — ideal para demonstrar dados sendo gerados em tempo real durante a apresentação.

---

## Teste Local com Docker

Antes da apresentação, teste tudo localmente com Docker.

### Pré-requisito

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado e rodando

### Subir o ambiente

```powershell
# Na raiz do projeto:
.\testar-local.ps1
```

O script sobe o primário (3306), a réplica (3307), configura a replicação e valida que está funcionando.

Ou manualmente:

```powershell
docker compose up -d
```

### Comandos úteis

```powershell
# Verificar status da replicação
docker exec mysql-replica mysql -uroot -proot -e "SHOW REPLICA STATUS\G"

# Acessar o primário via MySQL
docker exec -it mysql-primary mysql -uroot -proot aula-db

# Acessar a réplica via MySQL
docker exec -it mysql-replica mysql -uroot -proot aula-db

# Parar (mantém dados)
docker compose down

# Parar e apagar dados
docker compose down -v
```

### O que verificar

```
Replica_IO_Running: Yes     ← deve ser "Yes"
Replica_SQL_Running: Yes    ← deve ser "Yes"
Seconds_Behind_Source: 0    ← deve ser 0 ou muito baixo
Last_IO_Error:              ← deve estar vazio
```

### Se a replicação parar

```powershell
docker exec mysql-replica mysql -uroot -proot -e "STOP REPLICA"
docker exec mysql-replica mysql -uroot -proot -e "RESET REPLICA ALL"
# Depois reconfigure com CHANGE REPLICATION SOURCE TO...
docker exec mysql-replica mysql -uroot -proot -e "START REPLICA"
```

---

## Como Executar na Apresentação

> O Docker **não é necessário** na apresentação — você conecta direto nos hosts do professor.

**1. Editar os hosts (sem recompilar):**

- `java-app/target/config.properties` → IPs do professor
- `api-rest/.env` → IP da réplica do professor

**2. Terminal 1 — Java:**
```powershell
cd java-app\target
java -cp "db-replicacao.jar;libs/*" com.replicacao.db.Main
```

**3. Terminal 2 — API REST:**
```powershell
cd api-rest
npm start
```

**4. Abrir no navegador:**
```
http://localhost:3000
```

> Se o banco do professor não tiver as tabelas criadas, execute antes:
> ```powershell
> mysql -h IP_DO_PROFESSOR -uroot -p < sql\schema.sql
> ```

---

## Como Testar

```powershell
curl http://localhost:3000/pedidos/1
curl http://localhost:3000/clientes/1/pedidos
curl http://localhost:3000/produtos/baixo-estoque
curl http://localhost:3000/relatorios/vendas
curl http://localhost:3000/health
```

---

## Fluxo de Ciclos

```
FASE INICIAL (executada uma vez ao subir)
├── [WRITE → Primário] INSERT 5 clientes
└── [WRITE → Primário] INSERT 10 produtos
     └── Aguarda 2s para réplica sincronizar

CICLO N (repetido indefinidamente)
│
├── [READ  → Réplica]  SELECT clientes → escolhe 1 aleatório
├── [READ  → Réplica]  SELECT produtos → escolhe 1 a 3 aleatórios
│
├── [WRITE → Primário] INSERT pedido + itens (transação única)
├── [WRITE → Primário] UPDATE pedido SET status = novo_status
├── [WRITE → Primário] DELETE cliente sem pedidos (a cada 5 ciclos)
│                      └── se todos tiverem pedidos: pula e avisa no console
│
└── [READ  → Réplica]
    ├── SELECT pedido por ID (JOIN cliente)
    ├── SELECT itens do pedido (JOIN produto)
    ├── SELECT últimos 5 pedidos do cliente
    └── SELECT COUNT(*) / AVG(valor_total) / SUM(valor_total)
```

---

## Reiniciar os Dados do Zero

Para zerar todos os pedidos, clientes e produtos e começar a contagem do início:

```powershell
docker compose down -v   # apaga os volumes com todos os dados
docker compose up -d     # sobe tudo limpo
```

Aguarde ~30 segundos e rode a aplicação Java normalmente.

---

## Escalabilidade Horizontal

Para adicionar réplicas, separe por vírgula em ambos os arquivos:

**`config.properties`:**
```properties
db.read.replicas=IP1:3307,IP2:3307,IP3:3307
```

**`.env`:**
```env
DB_READ_REPLICAS=IP1:3307,IP2:3307,IP3:3307
```

Nenhuma alteração de código é necessária. O `ConnectionManager.java` e o `replicaPool.js` distribuem automaticamente as leituras em round-robin entre todas as réplicas listadas.

---

## Endpoints da API REST

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/` | Dashboard visual |
| `GET` | `/pedidos/:id` | Pedido por ID com cliente e itens |
| `GET` | `/clientes/:id/pedidos` | Histórico de pedidos de um cliente |
| `GET` | `/produtos/baixo-estoque` | Produtos com estoque abaixo do limiar |
| `GET` | `/relatorios/vendas` | SUM / COUNT / AVG + top 5 produtos |
| `GET` | `/health` | Verificação de saúde da API |

---

## Dependências

### Java
- **MySQL Connector/J 8.3** — driver JDBC oficial do MySQL

### Node.js
- **express 4.x** — framework HTTP
- **mysql2 3.x** — driver MySQL com suporte a Promises e pool de conexões
- **dotenv 16.x** — carregamento de variáveis de ambiente
