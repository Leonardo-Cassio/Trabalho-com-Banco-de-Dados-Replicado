'use strict';

const { Router } = require('express');
const db = require('../database/replicaPool');

const router = Router();

/**
 * GET /produtos/baixo-estoque
 * Retorna produtos com estoque abaixo do limiar configurado em LOW_STOCK_THRESHOLD.
 */
router.get('/baixo-estoque', async (req, res) => {
  const limiar = Number(process.env.LOW_STOCK_THRESHOLD) || 10;

  try {
    const produtos = await db.query(
      `SELECT id, descricao, categoria, valor, estoque
       FROM produto
       WHERE estoque < ?
       ORDER BY estoque ASC`,
      [limiar]
    );

    return res.json({
      limiar_estoque: limiar,
      total: produtos.length,
      produtos,
    });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ erro: 'Erro interno ao buscar produtos com baixo estoque.' });
  }
});

module.exports = router;
