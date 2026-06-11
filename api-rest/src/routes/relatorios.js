'use strict';

const { Router } = require('express');
const db = require('../database/replicaPool');

const router = Router();

/**
 * GET /relatorios/vendas
 *
 * Relatório consolidado de vendas executando 3 queries na RÉPLICA:
 *   1. resumo_geral  — COUNT, SUM, AVG, MIN, MAX sobre todos os pedidos
 *   2. por_status    — agrupamento por status com COUNT e SUM
 *   3. top_5_produtos — os 5 produtos mais vendidos por quantidade
 *
 * Todas as queries usam funções de agregação e vão exclusivamente à réplica.
 * O ?? 0 nos valores do resumo evita retornar null quando a tabela está vazia.
 */
router.get('/vendas', async (req, res) => {
  try {
    // Query 1: resumo geral — uma única linha com os totais globais
    const [resumo] = await db.query(
      `SELECT
         COUNT(*)         AS total_pedidos,
         SUM(valor_total) AS total_vendido,
         AVG(valor_total) AS media_por_pedido,
         MIN(valor_total) AS menor_pedido,
         MAX(valor_total) AS maior_pedido
       FROM pedido`
    );
    // Desestruturação: [resumo] pega só o primeiro (e único) elemento do array

    // Query 2: breakdown por status — N linhas, uma por status existente
    const porStatus = await db.query(
      `SELECT status,
              COUNT(*)         AS quantidade,
              SUM(valor_total) AS total_valor
       FROM pedido
       GROUP BY status
       ORDER BY quantidade DESC`
    );

    // Query 3: top 5 produtos mais vendidos em quantidade de unidades
    // JOIN entre pedido_item e produto para trazer o nome do produto
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
        // ?? 0 → se o campo for null (tabela vazia), usa 0 em vez de null
        total_vendido:    Number(resumo.total_vendido   ?? 0).toFixed(2),
        media_por_pedido: Number(resumo.media_por_pedido ?? 0).toFixed(2),
        menor_pedido:     Number(resumo.menor_pedido    ?? 0).toFixed(2),
        maior_pedido:     Number(resumo.maior_pedido    ?? 0).toFixed(2),
      },
      por_status:     porStatus,
      top_5_produtos: topProdutos,
    });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ erro: 'Erro interno ao gerar relatório de vendas.' });
  }
});

module.exports = router;
