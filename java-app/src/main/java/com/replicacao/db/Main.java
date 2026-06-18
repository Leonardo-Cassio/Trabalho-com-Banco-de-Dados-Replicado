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
 */

public class Main {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Replicação de Banco de Dados - MySQL        ║");
        System.out.println("║   Gabriel Fillip e Leonardo Cassio            ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        // Garante a leitura do config.properties
        AppConfig config = new AppConfig();

        // ConnectionManager é o que separa os fluxos(réplica) e escrita(primário).
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
            // Inserções feitas apenas uma vez ao iniciar a aplicação.
            service.cadastrarClientes(15);
            service.cadastrarProdutos();

            // Aguarda a réplica sincronizar os dados recém-inseridos.
            // A replicação é assíncrona;
            System.out.println("\n>>> Aguardando replicação sincronizar (2s)...");
            Thread.sleep(2000);

            int ciclo     = 0;
            int maxCiclos = config.getCycles(); 

            System.out.println(">>> Iniciando ciclos de pedidos. Pressione Ctrl+C para parar.");

            // ── LOOP PRINCIPAL ────────────────────────────────────────
            while (maxCiclos == 0 || ciclo < maxCiclos) {
                ciclo++;
                System.out.printf("%n╔══════════ CICLO %-4d ══════════╗%n", ciclo);

                // ESCRITA 1 — INSERT pedido e seus itens no primário
                Pedido pedido = service.criarPedido();

                // ESCRITA 2 — UPDATE: avança o status do pedido recém-criado no primário.
                service.atualizarStatusPedido(pedido);

                // ESCRITA 3 — DELETE: a cada 5 ciclos remove um cliente sem pedidos.
                if (ciclo % 5 == 0) {
                    service.removerClienteAntigo();
                }

                // LEITURA — todas as consultas 4.1 a 4.4 usam SOMENTE a réplica
                service.executarConsultas(pedido);

                System.out.printf("╚═════════ Fim Ciclo %-4d ════════╝%n", ciclo);
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
