'use strict';

const { Router } = require('express');
const db = require('../database/replicaPool');

const router = Router();

/**
 * GET /pedidos/:id
 *
 * Busca um pedido pelo ID, traz os dados do cliente via JOIN,
 * e em seguida busca todos os itens do pedido com JOIN no produto.
 *
 * LEITURA EXCLUSIVA NA RÉPLICA:
 *   db.query() → replicaPool.next() → pool da réplica → SELECT no MySQL Réplica
 *
 * FLUXO:
 *   1. Valida se o ID é um inteiro positivo
 *   2. SELECT pedido + JOIN cliente → retorna 0 ou 1 linha
 *   3. Se não encontrou → 404
 *   4. SELECT itens do pedido + JOIN produto → retorna N linhas
 *   5. Monta e devolve JSON com pedido + array de itens
 */
router.get('/:id', async (req, res) => {
  // Converte o parâmetro de URL (string) para número
  const id = Number(req.params.id);

  // Valida antes de ir ao banco — evita SQL desnecessário com IDs inválidos
  if (!Number.isInteger(id) || id <= 0) {
    return res.status(400).json({ erro: 'ID de pedido inválido.' });
  }

  try {
    // Query 1: busca o pedido com dados do cliente (JOIN)
    // Vai para a RÉPLICA via db.query()
    const pedidos = await db.query(
      `SELECT p.id, p.cliente_id, c.nome AS cliente_nome, c.email AS cliente_email,
              p.valor_total, p.status, p.criado_em, p.criado_por
       FROM pedido p
       JOIN cliente c ON c.id = p.cliente_id
       WHERE p.id = ?`,
      [id]
    );

    // db.query() sempre retorna array — array vazio significa não encontrado
    if (pedidos.length === 0) {
      return res.status(404).json({ erro: `Pedido ${id} não encontrado.` });
    }

    const pedido = pedidos[0]; // só existe um pedido por ID

    // Query 2: busca os itens do pedido com nome do produto (JOIN)
    // Vai para a próxima RÉPLICA em round-robin
    const itens = await db.query(
      `SELECT pi.id, pi.produto_id, pr.descricao AS produto, pr.categoria,
              pi.quantidade, pi.valor_unitario,
              (pi.quantidade * pi.valor_unitario) AS subtotal
       FROM pedido_item pi
       JOIN produto pr ON pr.id = pi.produto_id
       WHERE pi.pedido_id = ?`,
      [id]
    );

    // Spread operator (...pedido) copia todos os campos do pedido
    // e adiciona o array de itens ao mesmo objeto JSON
    return res.json({ ...pedido, itens });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ erro: 'Erro interno ao buscar pedido.' });
  }
});

module.exports = router;
