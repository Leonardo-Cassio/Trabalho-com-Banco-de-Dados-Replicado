'use strict';

const { Router } = require('express');
const db = require('../database/replicaPool');

const router = Router();

/**
 * GET /pedidos/:id
 * Busca um pedido pelo ID com os dados do cliente associado.
 */
router.get('/:id', async (req, res) => {
  const id = Number(req.params.id);

  if (!Number.isInteger(id) || id <= 0) {
    return res.status(400).json({ erro: 'ID de pedido inválido.' });
  }

  try {
    const pedidos = await db.query(
      `SELECT p.id, p.cliente_id, c.nome AS cliente_nome, c.email AS cliente_email,
              p.valor_total, p.status, p.criado_em, p.criado_por
       FROM pedido p
       JOIN cliente c ON c.id = p.cliente_id
       WHERE p.id = ?`,
      [id]
    );

    if (pedidos.length === 0) {
      return res.status(404).json({ erro: `Pedido ${id} não encontrado.` });
    }

    const pedido = pedidos[0];

    const itens = await db.query(
      `SELECT pi.id, pi.produto_id, pr.descricao AS produto, pr.categoria,
              pi.quantidade, pi.valor_unitario,
              (pi.quantidade * pi.valor_unitario) AS subtotal
       FROM pedido_item pi
       JOIN produto pr ON pr.id = pi.produto_id
       WHERE pi.pedido_id = ?`,
      [id]
    );

    return res.json({ ...pedido, itens });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ erro: 'Erro interno ao buscar pedido.' });
  }
});

module.exports = router;
