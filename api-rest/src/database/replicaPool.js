'use strict';

const mysql = require('mysql2/promise');

/**
 * GERENCIADOR DE POOLS DE CONEXÃO — RÉPLICAS (Node.js)
 *
 * Equivalente ao ConnectionManager.java, mas para o lado da API REST.
 * Cria e gerencia um pool de conexões para cada réplica configurada.
 * A API lê SOMENTE das réplicas — não há nenhuma conexão com o primário aqui.
 *
 * POR QUE POOL E NÃO CONEXÃO DIRETA?
 *   Um pool mantém conexões abertas e reutilizáveis, evitando o overhead
 *   de abrir/fechar uma conexão a cada requisição HTTP recebida.
 *   connectionLimit: 10 significa até 10 conexões simultâneas por réplica.
 *
 * CONFIGURAÇÃO (lida do arquivo api-rest/.env):
 *   DB_READ_REPLICAS=host1:porta1,host2:porta2
 *   DB_DATABASE, DB_USERNAME, DB_PASSWORD
 *
 * BALANCEAMENTO EM ROUND-ROBIN:
 *   Cada chamada a db.query() usa a próxima réplica da lista.
 *   Com 1 réplica: todas as queries vão para ela.
 *   Com N réplicas: alterna — réplica 0, 1, 2, 0, 1, 2, ...
 *
 * USO:
 *   const db = require('./database/replicaPool');
 *   db.init();                        // chamado uma vez em server.js
 *   const rows = await db.query(sql, [params]);  // nas routes
 */
class ReplicaPool {
  constructor() {
    this._pools = []; // array de { label, pool } — um por réplica
    this._index = 0;  // contador para round-robin
  }

  /**
   * Inicializa os pools a partir das variáveis de ambiente (.env).
   * Chamado UMA VEZ em server.js antes de app.listen().
   *
   * Para cada réplica em DB_READ_REPLICAS, cria um pool mysql2 separado.
   * O pool é assíncrono e gerencia internamente a fila de conexões disponíveis.
   */
  init() {
    const replicasRaw = process.env.DB_READ_REPLICAS || '127.0.0.1:3307';
    const database    = process.env.DB_DATABASE       || 'aula-db';
    const user        = process.env.DB_USERNAME        || 'root';
    const password    = process.env.DB_PASSWORD        || 'root';

    // Suporta múltiplas réplicas separadas por vírgula: "IP1:3307,IP2:3307"
    const replicas = replicasRaw.split(',').map(s => s.trim()).filter(Boolean);

    if (replicas.length === 0) {
      throw new Error('Nenhuma réplica configurada em DB_READ_REPLICAS.');
    }

    // Cria um pool de conexões para cada réplica
    this._pools = replicas.map(replica => {
      const [host, port = '3306'] = replica.split(':');
      const pool = mysql.createPool({
        host,
        port:               Number(port),
        database,
        user,
        password,
        waitForConnections: true,  // requisições aguardam se todas as conexões estiverem em uso
        connectionLimit:    10,    // máximo de conexões simultâneas por réplica
        queueLimit:         0,     // fila ilimitada (0 = sem limite)
        timezone:           '-03:00', // horário de Brasília para evitar erros em campos DATETIME
      });
      console.log(`  [REPLICA] Pool criado: ${host}:${port}/${database}`);
      return { label: `${host}:${port}`, pool };
    });

    console.log(`  Total de réplicas configuradas: ${this._pools.length}`);
  }

  /**
   * Retorna o próximo pool disponível usando round-robin.
   * Chamado internamente por query() a cada requisição.
   *
   * O módulo (%) garante que o índice nunca ultrapasse o tamanho do array.
   * Exemplo com 2 réplicas: 0 % 2 = 0, 1 % 2 = 1, 2 % 2 = 0, 3 % 2 = 1, ...
   */
  next() {
    if (this._pools.length === 0) throw new Error('ReplicaPool não inicializado. Chame db.init() primeiro.');
    const entry = this._pools[this._index % this._pools.length];
    this._index++;
    return entry;
  }

  /**
   * Executa um SELECT na próxima réplica disponível e retorna as linhas.
   * Chamado pelas routes (pedidos.js, clientes.js, produtos.js, relatorios.js).
   *
   * pool.execute() usa prepared statements internamente (mais seguro que query() puro),
   * protegendo contra SQL injection nos parâmetros passados como array.
   *
   * @param {string} sql    - Query SQL com placeholders "?"
   * @param {Array}  params - Valores que substituem os "?" na ordem
   * @returns {Array}       - Array de objetos com as linhas retornadas
   */
  async query(sql, params = []) {
    const { label, pool } = this.next();
    console.log(`  [READ] Réplica usada: ${label}`);
    const [rows] = await pool.execute(sql, params);
    return rows;
  }
}

// Exporta uma instância única (singleton) — todas as routes compartilham o mesmo pool
module.exports = new ReplicaPool();
