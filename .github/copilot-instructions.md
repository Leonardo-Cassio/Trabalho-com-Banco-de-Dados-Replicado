# Instruções do Projeto — Replicação de Banco de Dados

Este arquivo contém todo o contexto do projeto para apoiar alterações futuras.
Ao receber uma dúvida ou pedido de alteração, consulte este arquivo primeiro.

---

## O que é este projeto

Atividade acadêmica que demonstra replicação MySQL com separação de leitura e escrita.

- **Autores:** Gabriel Fillip e Leonardo Cassio
- **Disciplina:** Banco de Dados / Infraestrutura
- **Campo `criado_por`** em todas as tabelas deve sempre conter o valor:
  `Gabriel Fillip e Leonardo Cassio`

### Componentes

| Componente | Linguagem | Diretório | Porta |
|---|---|---|---|
| Aplicação geradora de dados | Java 17 + JDBC | `java-app/` | — |
| API REST | Node.js + Express | `api-rest/` | 3000 |
| MySQL Primário (escrita) | Docker | — | 3306 |
| MySQL Réplica (leitura) | Docker | — | 3307 |

---

## Arquitetura de separação read/write

**Regra absoluta do projeto:**
- Todo `INSERT`, `UPDATE`, `DELETE` → host primário (3306)
- Todo `SELECT`, `JOIN`, agregações → réplica (3307)
- A API REST lê **somente** da réplica
- A aplicação Java escreve **somente** no primário

### Como a separação é implementada no Java

A classe `ConnectionManager.java` tem dois métodos distintos:
- `getWriteConnection()` → abre conexão com o host primário
- `getReadConnection()` → abre conexão com a próxima réplica em round-robin

Cada repository usa explicitamente um ou outro:
- `inserir()` → sempre chama `getWriteConnection()`
- `listarTodos()`, `buscarPorId()`, etc → sempre chamam `getReadConnection()`

### Como a separação é implementada na API REST

O arquivo `replicaPool.js` gerencia um pool de conexões por réplica.
O método `db.query(sql, params)` sempre usa uma réplica via round-robin.
**A API não tem nenhuma conexão com o primário** — ela é somente leitura.

---

## Estrutura de arquivos

```
.github/
  copilot-instructions.md     ← este arquivo

sql/
  schema.sql                  ← DDL das 4 tabelas (usar para setup manual)

docker/
  init-primary.sql            ← executado automaticamente ao criar o container primário
  setup-replica.sh            ← configura a replicação MySQL entre os containers
  primary.cnf                 ← config MySQL do primário (ATENÇÃO: ignorado no Docker — ver abaixo)
  replica.cnf                 ← config MySQL da réplica  (ATENÇÃO: ignorado no Docker — ver abaixo)

docker-compose.yml            ← sobe primário + réplica + container de setup

java-app/
  pom.xml                     ← dependência: mysql-connector-j 8.3.0, Java 17
  src/main/resources/
    config.properties         ← ARQUIVO A ALTERAR para mudar hosts/portas/credenciais
  src/main/java/com/replicacao/db/
    Main.java                 ← ponto de entrada, loop de ciclos
    config/AppConfig.java     ← lê config.properties
    connection/ConnectionManager.java  ← gerencia conexões write/read com round-robin
    model/                    ← Cliente, Produto, Pedido, PedidoItem
    repository/               ← ClienteRepository, ProdutoRepository, PedidoRepository
    service/DataGeneratorService.java  ← gera dados aleatórios e executa as consultas

api-rest/
  .env                        ← ARQUIVO A ALTERAR para mudar hosts/portas/credenciais
  src/server.js               ← bootstrap Express, serve o dashboard estático
  src/database/replicaPool.js ← pools de conexão por réplica, round-robin
  src/routes/
    pedidos.js                ← GET /pedidos/:id
    clientes.js               ← GET /clientes/:id/pedidos
    produtos.js               ← GET /produtos/baixo-estoque
    relatorios.js             ← GET /relatorios/vendas
  src/public/index.html       ← dashboard visual acessível em localhost:3000
```

---

## Banco de dados

### Tabelas e relacionamentos

```
cliente (1) ──< pedido (1) ──< pedido_item >── (1) produto
```

### Detalhe importante: tamanho de `criado_por`

O campo `criado_por` em todas as tabelas (`cliente`, `produto`, `pedido`) é `VARCHAR(50)`.

**Por que 50 e não 30 (como estava no enunciado original)?**
O valor inserido é `"Gabriel Fillip e Leonardo Cassio"` que tem **32 caracteres**.
`VARCHAR(30)` causava erro `Data too long for column 'criado_por'`.
A correção foi aplicada em:
- `sql/schema.sql`
- `docker/init-primary.sql`

### Observação sobre `pedido.criado_por` no DDL

Na tabela `pedido`, a coluna tem dois espaços antes de `VARCHAR`:
```sql
criado_por  VARCHAR(50)    NOT NULL,
```
Isso é apenas formatação para alinhar com as outras colunas. Não tem impacto funcional.

---

## Configuração para trocar de ambiente

### Ao receber os hosts do professor

**Arquivo 1:** `java-app/src/main/resources/config.properties`
```properties
db.write.host=IP_PRIMARIO
db.write.port=3306
db.write.database=aula-db
db.write.username=USUARIO
db.write.password=SENHA

db.read.replicas=IP_REPLICA:3307
db.read.database=aula-db
db.read.username=USUARIO
db.read.password=SENHA

app.cycle.interval.ms=3000   # intervalo entre ciclos em ms
app.cycles=0                 # 0 = infinito
```

**Arquivo 2:** `api-rest/.env`
```env
PORT=3000
DB_READ_REPLICAS=IP_REPLICA:3307
DB_DATABASE=aula-db
DB_USERNAME=USUARIO
DB_PASSWORD=SENHA
LOW_STOCK_THRESHOLD=10
```

### Para múltiplas réplicas

Basta separar por vírgula em ambos os arquivos:
```
db.read.replicas=IP1:3307,IP2:3307,IP3:3307
DB_READ_REPLICAS=IP1:3307,IP2:3307,IP3:3307
```
O round-robin distribui automaticamente sem nenhuma alteração de código.

### Após trocar os hosts

- API REST: só reiniciar (`npm start`) — lê `.env` na inicialização
- Java: **precisa recompilar** (`mvn clean package -q`) porque o `config.properties` é empacotado dentro do JAR

---

## Como executar (ambiente local com Docker)

### Pré-requisito
Docker Desktop instalado e rodando.

### Subir tudo de uma vez
```powershell
.\testar-local.ps1
```

### Manualmente
```powershell
# 1. Subir containers
docker compose up -d

# 2. Aguardar ~30s para o setup de replicação terminar
docker logs replica-setup

# 3. Java (Terminal 1)
cd java-app
mvn clean package -q
java -cp "target/db-replicacao.jar;target/libs/*" com.replicacao.db.Main

# 4. API REST (Terminal 2)
cd api-rest
npm install
npm start

# 5. Abrir no navegador
# http://localhost:3000
```

### Parar o ambiente
```powershell
docker compose down        # mantém os dados
docker compose down -v     # apaga os dados (começa do zero)
```

---

## Fluxo de ciclos da aplicação Java

### Fase inicial (executa uma vez)
1. Insere 5 clientes no primário (nomes aleatórios da lista `NOMES[]`)
2. Insere 10 produtos no primário (lista fixa `PRODUTOS[]`)
3. Aguarda 2 segundos para a réplica sincronizar

### Por ciclo (loop infinito)
1. **[READ réplica]** Lista todos os clientes → escolhe 1 aleatoriamente
2. **[READ réplica]** Lista todos os produtos → escolhe 1 a 3 aleatoriamente
3. **[WRITE primário]** Insere pedido + itens em uma única transação
4. **[READ réplica]** Busca pedido por ID (JOIN cliente)
5. **[READ réplica]** Busca itens do pedido (JOIN produto)
6. **[READ réplica]** Busca últimos 5 pedidos do cliente
7. **[READ réplica]** Executa COUNT/AVG/SUM agregados

---

## Endpoints da API REST

| Método | Rota | Descrição | Arquivo |
|---|---|---|---|
| GET | `/pedidos/:id` | Pedido com cliente e itens | `routes/pedidos.js` |
| GET | `/clientes/:id/pedidos` | Todos os pedidos de um cliente | `routes/clientes.js` |
| GET | `/produtos/baixo-estoque` | Produtos com estoque < `LOW_STOCK_THRESHOLD` | `routes/produtos.js` |
| GET | `/relatorios/vendas` | SUM/COUNT/AVG + top 5 produtos | `routes/relatorios.js` |
| GET | `/health` | Health check | `server.js` |
| GET | `/` | Dashboard visual | `public/index.html` |

---

## Dashboard visual

Arquivo: `api-rest/src/public/index.html`
Acessível em: `http://localhost:3000`

Funcionalidades:
- Buscar pedido por ID (mostra pedido + itens em tabela)
- Buscar pedidos de um cliente (mostra histórico)
- Produtos com baixo estoque (destaca em vermelho os críticos)
- Relatório de vendas com KPIs e **auto-refresh** configurável (3s/5s/10s)

Para alterar o visual, edite apenas o `index.html` — é HTML/CSS/JS puro, sem frameworks.

---

## Problemas conhecidos e soluções aplicadas

### 1. `criado_por` VARCHAR(30) → VARCHAR(50)
**Erro:** `Data too long for column 'criado_por' at row 1`
**Causa:** "Gabriel Fillip e Leonardo Cassio" tem 32 caracteres, `VARCHAR(30)` não comporta.
**Solução:** Alterado para `VARCHAR(50)` em `schema.sql` e `init-primary.sql`.
**Atenção:** Se o banco já existir com `VARCHAR(30)`, execute manualmente:
```sql
ALTER TABLE cliente MODIFY criado_por VARCHAR(50) NOT NULL;
ALTER TABLE produto  MODIFY criado_por VARCHAR(50) NOT NULL;
ALTER TABLE pedido   MODIFY criado_por VARCHAR(50) NOT NULL;
```

### 2. Duplicate entry no email
**Erro:** `Duplicate entry 'nome@email.com' for key 'cliente.email'`
**Causa:** O email era gerado com sufixo fixo (0,1,2...) — colide em execuções consecutivas.
**Solução:** O email agora inclui o timestamp da execução (`System.currentTimeMillis()`).
Arquivo: `DataGeneratorService.java` — campo `runId` e método `gerarEmail()`.

### 3. Réplica retornando vazio (replication lag)
**Erro:** `Nenhum produto cadastrado` logo após inserir os produtos.
**Causa:** A réplica ainda não tinha sincronizado os dados recém-inseridos no primário.
**Solução 1:** Pausa de 2 segundos em `Main.java` após a fase inicial.
**Solução 2:** `criarPedido()` tenta até 5 vezes com 1s de espera se a réplica estiver vazia.

### 4. Arquivos `.cnf` ignorados pelo MySQL no Docker
**Erro:** `Replica_IO_Running: No` — `server ids must be different`
**Causa:** Arquivos `.cnf` montados do Windows são `world-writable`, o MySQL os ignora por segurança.
**Consequência:** Ambos os containers ficavam com `server-id=1` (default), impossibilitando a replicação.
**Solução:** As opções MySQL foram movidas para o `command:` no `docker-compose.yml`:
```yaml
command: >
  mysqld
  --server-id=1          # primário
  --log-bin=mysql-bin
  --binlog-format=ROW
  --binlog-do-db=aula-db
```
Os arquivos `.cnf` ainda existem no repositório mas não são mais montados no Docker.

### 5. `setup-replica.sh` conectando em localhost
**Erro:** Container de setup ficava em loop infinito esperando a réplica.
**Causa:** O script usava `mysql -uroot -proot` sem `-h`, conectando em `localhost` dentro do container de setup (que não tem MySQL).
**Solução:** Todos os comandos no script agora especificam o host explicitamente:
- `mysql -h mysql-primary ...` para o primário
- `mysql -h mysql-replica ...` para a réplica

---

## Dependências

### Java (`pom.xml`)
- `com.mysql:mysql-connector-j:8.3.0` — driver JDBC oficial MySQL

### Node.js (`package.json`)
- `express:^4.19.2` — framework HTTP
- `mysql2:^3.9.7` — driver MySQL com suporte a Promises e pool de conexões
- `dotenv:^16.4.5` — carregamento de variáveis de ambiente

---

## Checklist para apresentação

- [ ] Docker Desktop rodando
- [ ] `docker compose up -d` executado e `replica-setup` finalizado com `Replica_IO_Running: Yes`
- [ ] `config.properties` com o host do professor
- [ ] `.env` com o host do professor
- [ ] Java compilado com `mvn clean package -q`
- [ ] Java rodando (gerando pedidos no console)
- [ ] API rodando (`npm start`)
- [ ] Dashboard abrindo em `localhost:3000`
- [ ] Auto-refresh do relatório ativo para mostrar dados em tempo real
