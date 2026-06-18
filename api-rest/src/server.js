'use strict';

/**
 * PONTO DE ENTRADA DA API REST (Node.js)
 * Inicializa o servidor Express, registra as rotas e sobe na porta configurada.
 * Toda leitura de dados é delegada às routes, que usam replicaPool.js para
 * consultar exclusivamente as réplicas MySQL — nunca o primário.
 */

// dotenv lê o arquivo .env e injeta as variáveis em process.env
require('dotenv').config();

const path       = require('path');
const express    = require('express');
const db         = require('./database/replicaPool');
const pedidos    = require('./routes/pedidos');
const clientes   = require('./routes/clientes');
const produtos   = require('./routes/produtos');
const relatorios = require('./routes/relatorios');

const app  = express();
const PORT = process.env.PORT || 3000;

// Permite que o Express leia corpo de requisições em formato JSON
app.use(express.json());

// Middleware de log: imprime método e URL de toda requisição recebida
app.use((req, _res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
  next();
});

// Serve o dashboard visual (index.html e assets) na raiz "/"
// express.static mapeia GET / → src/public/index.html automaticamente
app.use(express.static(path.join(__dirname, 'public')));

// Registra os roteadores — cada um responde por um prefixo de URL
app.use('/pedidos',    pedidos);    // GET /pedidos/:id
app.use('/clientes',   clientes);   // GET /clientes/:id/pedidos
app.use('/produtos',   produtos);   // GET /produtos/baixo-estoque
app.use('/relatorios', relatorios); // GET /relatorios/vendas

// Verificar se a API está no ar
app.get('/health', (_req, res) => res.json({ status: 'ok', timestamp: new Date() }));

// Fallback para rotas não registradas — retorna 404 em JSON
app.use((_req, res) => res.status(404).json({ erro: 'Rota não encontrada.' }));

// Inicializa os pools de conexão com as réplicas (lê DB_READ_REPLICAS do .env)
db.init();

app.listen(PORT, () => {
  console.log(`\n╔══════════════════════════════════════════════╗`);
  console.log(`║  API REST - Replicação de Banco de Dados     ║`);
  console.log(`║  Gabriel Fillip e Leonardo Cassio            ║`);
  console.log(`╚══════════════════════════════════════════════╝`);
  console.log(`  Servidor rodando em http://localhost:${PORT}`);
  console.log(`\n  Endpoints disponíveis:`);
  console.log(`    GET /pedidos/:id`);
  console.log(`    GET /clientes/:id/pedidos`);
  console.log(`    GET /produtos/baixo-estoque`);
  console.log(`    GET /relatorios/vendas`);
  console.log(`    GET /health`);
});
