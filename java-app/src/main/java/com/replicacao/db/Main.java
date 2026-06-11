package com.replicacao.db;

import com.replicacao.db.config.AppConfig;
import com.replicacao.db.connection.ConnectionManager;
import com.replicacao.db.model.Pedido;
import com.replicacao.db.repository.ClienteRepository;
import com.replicacao.db.repository.PedidoRepository;
import com.replicacao.db.repository.ProdutoRepository;
import com.replicacao.db.service.DataGeneratorService;

/**
 * Ponto de entrada da aplicação Java.
 *
 * RESPONSABILIDADE:
 *   Orquestra o ciclo contínuo de geração de dados, demonstrando
 *   na prática a separação entre host de escrita (primário) e hosts
 *   de leitura (réplicas).
 *
 * FLUXO GERAL:
 *   1. Lê as configurações (IPs, portas, credenciais) do config.properties
 *   2. Cria os repositórios — cada um recebe o ConnectionManager para
 *      saber onde escrever e onde ler
 *   3. Fase inicial: insere clientes e produtos UMA ÚNICA VEZ no primário
 *   4. Loop infinito:
 *        a) INSERT pedido + itens     → primário  (escrita)
 *        b) UPDATE status do pedido   → primário  (escrita)
 *        c) DELETE cliente (ciclo %5) → primário  (escrita)
 *        d) SELECT consultas 4.1–4.4  → réplica   (leitura)
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Replicação de Banco de Dados - MySQL        ║");
        System.out.println("║   Gabriel Fillip e Leonardo Cassio            ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        // Carrega config.properties (externo ao lado do JAR tem prioridade;
        // se não existir, usa o embutido dentro do JAR)
        AppConfig config = new AppConfig();

        // ConnectionManager é o único ponto que sabe os IPs de escrita e leitura.
        // Todos os repositórios recebem esse objeto e chamam getWriteConnection()
        // ou getReadConnection() conforme a operação.
        ConnectionManager cm = new ConnectionManager(config);

        // Repositórios — encapsulam os SQLs de cada entidade.
        // Recebem o mesmo ConnectionManager, mas cada método escolhe
        // internamente se vai ao primário ou à réplica.
        ClienteRepository clienteRepo = new ClienteRepository(cm);
        ProdutoRepository produtoRepo  = new ProdutoRepository(cm);
        PedidoRepository  pedidoRepo   = new PedidoRepository(cm);

        // Serviço que gera os dados aleatórios e coordena as operações
        DataGeneratorService service = new DataGeneratorService(clienteRepo, produtoRepo, pedidoRepo);

        try {
            // ── FASE INICIAL ─────────────────────────────────────────
            // Inserções feitas apenas uma vez ao iniciar a aplicação.
            // Sem esses dados no primário não há o que selecionar nas réplicas.
            service.cadastrarClientes(15);
            service.cadastrarProdutos();

            // Aguarda a réplica sincronizar os dados recém-inseridos.
            // A replicação é assíncrona; sem essa pausa os primeiros SELECTs
            // na réplica podem retornar vazio (lag de replicação).
            System.out.println("\n>>> Aguardando replicação sincronizar (2s)...");
            Thread.sleep(2000);

            int ciclo     = 0;
            int maxCiclos = config.getCycles(); // 0 = infinito

            System.out.println(">>> Iniciando ciclos de pedidos. Pressione Ctrl+C para parar.");

            // ── LOOP PRINCIPAL ────────────────────────────────────────
            while (maxCiclos == 0 || ciclo < maxCiclos) {
                ciclo++;
                System.out.printf("%n╔══════════ CICLO %-4d ══════════╗%n", ciclo);

                // ESCRITA 1 — INSERT pedido e seus itens no primário
                Pedido pedido = service.criarPedido();

                // ESCRITA 2 — UPDATE: avança o status do pedido recém-criado no primário.
                // Demonstra que operações de escrita (inclusive UPDATE) vão ao primário.
                service.atualizarStatusPedido(pedido);

                // ESCRITA 3 — DELETE: a cada 5 ciclos remove um cliente sem pedidos.
                // Usa a réplica para ENCONTRAR o candidato (SELECT) e o primário
                // para EXECUTAR a remoção (DELETE) — demonstra os dois fluxos.
                if (ciclo % 5 == 0) {
                    service.removerClienteAntigo();
                }

                // LEITURA — todas as consultas 4.1 a 4.4 usam SOMENTE a réplica
                service.executarConsultas(pedido);

                System.out.printf("╚═════════ Fim Ciclo %-4d ════════╝%n", ciclo);

                // Pausa configurável entre ciclos (app.cycle.interval.ms no config)
                if (maxCiclos == 0 || ciclo < maxCiclos) {
                    Thread.sleep(config.getCycleIntervalMs());
                }
            }

            System.out.println("\nAplicação finalizada após " + ciclo + " ciclo(s).");

        } catch (InterruptedException e) {
            // Ctrl+C ou Thread.interrupt() — encerramento limpo sem stack trace
            System.out.println("\nAplicação interrompida pelo usuário.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("\nErro fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
