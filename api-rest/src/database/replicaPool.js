'use strict';

const mysql = require('mysql2/promise');

/**
 * Gerencia pools de conexão para N réplicas de leitura.
 * A seleção é feita em round-robin para distribuir a carga.
 */
class ReplicaPool {
  constructor() {
    this._pools = [];
    this._index = 0;
  }

  /**
   * Inicializa os pools a partir das configurações de ambiente.
   * Formato da env DB_READ_REPLICAS: "host1:porta1,host2:porta2,..."
   */
  init() {
    const replicasRaw = process.env.DB_READ_REPLICAS || '127.0.0.1:3307';
    const database    = process.env.DB_DATABASE       || 'aula-db';
    const user        = process.env.DB_USERNAME        || 'root';
    const password    = process.env.DB_PASSWORD        || 'root';

    const replicas = replicasRaw.split(',').map(s => s.trim()).filter(Boolean);

    if (replicas.length === 0) {
      throw new Error('Nenhuma réplica configurada em DB_READ_REPLICAS.');
    }

    this._pools = replicas.map(replica => {
      const [host, port = '3306'] = replica.split(':');
      const pool = mysql.createPool({
        host,
        port: Number(port),
        database,
        user,
        password,
        waitForConnections: true,
        connectionLimit:    10,
        queueLimit:         0,
        timezone:           '-03:00',
      });
      console.log(`  [REPLICA] Pool criado: ${host}:${port}/${database}`);
      return { label: `${host}:${port}`, pool };
    });

    console.log(`  Total de réplicas configuradas: ${this._pools.length}`);
  }

  /**
   * Retorna o próximo pool disponível usando round-robin.
   * @returns {{ label: string, pool: Pool }}
   */
  next() {
    if (this._pools.length === 0) throw new Error('ReplicaPool não inicializado.');
    const entry = this._pools[this._index % this._pools.length];
    this._index++;
    return entry;
  }

  /** Executa uma query SELECT na próxima réplica disponível. */
  async query(sql, params = []) {
    const { label, pool } = this.next();
    console.log(`  [READ] Réplica usada: ${label}`);
    const [rows] = await pool.execute(sql, params);
    return rows;
  }
}

module.exports = new ReplicaPool();
