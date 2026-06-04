'use strict';

const { Router } = require('express');
const db = require('../database/replicaPool');

const router = Router();

/**
 * GET /clientes/:id/pedidos
 * Retorna todos os pedidos de um cliente com seus respectivos itens.
 */
router.get('/:id/pedidos', async (req, res) => {
  const clienteId = Number(req.params.id);

  if (!Number.isInteger(clienteId) || clienteId <= 0) {
    return res.status(400).json({ erro: 'ID de cliente inválido.' });
  }

  try {
    const clientes = await db.query(
      'SELECT id, nome, email FROM cliente WHERE id = ?',
      [clienteId]
    );

    if (clientes.length === 0) {
      return res.status(404).json({ erro: `Cliente ${clienteId} não encontrado.` });
    }

    const pedidos = await db.query(
      `SELECT p.id, p.valor_total, p.status, p.criado_em,
              COUNT(pi.id) AS total_itens
       FROM pedido p
       LEFT JOIN pedido_item pi ON pi.pedido_id = p.id
       WHERE p.cliente_id = ?
       GROUP BY p.id
       ORDER BY p.id DESC`,
      [clienteId]
    );

    return res.json({
      cliente: clientes[0],
      total_pedidos: pedidos.length,
      pedidos,
    });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ erro: 'Erro interno ao buscar pedidos do cliente.' });
  }
});

module.exports = router;
