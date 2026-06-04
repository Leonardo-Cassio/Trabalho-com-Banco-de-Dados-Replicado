'use strict';

const { Router } = require('express');
const db = require('../database/replicaPool');

const router = Router();

/**
 * GET /relatorios/vendas
 * Relatório agregado de vendas usando SUM, COUNT e AVG.
 */
router.get('/vendas', async (req, res) => {
  try {
    const [resumo] = await db.query(
      `SELECT
         COUNT(*)         AS total_pedidos,
         SUM(valor_total) AS total_vendido,
         AVG(valor_total) AS media_por_pedido,
         MIN(valor_total) AS menor_pedido,
         MAX(valor_total) AS maior_pedido
       FROM pedido`
    );

    const porStatus = await db.query(
      `SELECT status,
              COUNT(*)         AS quantidade,
              SUM(valor_total) AS total_valor
       FROM pedido
       GROUP BY status
       ORDER BY quantidade DESC`
    );

    const topProdutos = await db.query(
      `SELECT pr.id, pr.descricao, pr.categoria,
              SUM(pi.quantidade)                       AS total_vendido,
              SUM(pi.quantidade * pi.valor_unitario)   AS receita_total
       FROM pedido_item pi
       JOIN produto pr ON pr.id = pi.produto_id
       GROUP BY pr.id
       ORDER BY total_vendido DESC
       LIMIT 5`
    );

    return res.json({
      resumo_geral: {
        total_pedidos:    Number(resumo.total_pedidos),
        total_vendido:    Number(resumo.total_vendido   ?? 0).toFixed(2),
        media_por_pedido: Number(resumo.media_por_pedido ?? 0).toFixed(2),
        menor_pedido:     Number(resumo.menor_pedido    ?? 0).toFixed(2),
        maior_pedido:     Number(resumo.maior_pedido    ?? 0).toFixed(2),
      },
      por_status:    porStatus,
      top_5_produtos: topProdutos,
    });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ erro: 'Erro interno ao gerar relatório de vendas.' });
  }
});

module.exports = router;
