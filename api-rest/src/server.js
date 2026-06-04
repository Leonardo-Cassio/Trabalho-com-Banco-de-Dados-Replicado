'use strict';

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

app.use(express.json());

// Logging básico de requisições
app.use((req, _res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
  next();
});

// Dashboard — serve o HTML na raiz
app.use(express.static(path.join(__dirname, 'public')));

// Rotas da API
app.use('/pedidos',    pedidos);
app.use('/clientes',   clientes);
app.use('/produtos',   produtos);
app.use('/relatorios', relatorios);

// Health check
app.get('/health', (_req, res) => res.json({ status: 'ok', timestamp: new Date() }));

// Handler de rota não encontrada
app.use((_req, res) => res.status(404).json({ erro: 'Rota não encontrada.' }));

// Inicializa pools de réplica e sobe o servidor
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
