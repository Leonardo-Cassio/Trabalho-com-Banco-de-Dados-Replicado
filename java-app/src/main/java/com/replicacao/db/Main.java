package com.replicacao.db;

import com.replicacao.db.config.AppConfig;
import com.replicacao.db.connection.ConnectionManager;
import com.replicacao.db.model.Pedido;
import com.replicacao.db.repository.ClienteRepository;
import com.replicacao.db.repository.PedidoRepository;
import com.replicacao.db.repository.ProdutoRepository;
import com.replicacao.db.service.DataGeneratorService;

/**
 * Ponto de entrada da aplicação Java de replicação de banco de dados.
 *
 * Fluxo:
 *  1. Insere clientes e produtos no host PRIMÁRIO (escrita).
 *  2. Em loop contínuo: cria pedidos (primário) e executa consultas na RÉPLICA (leitura).
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Replicação de Banco de Dados - MySQL        ║");
        System.out.println("║   Gabriel Fillip e Leonardo Cassio            ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        AppConfig       config  = new AppConfig();
        ConnectionManager cm    = new ConnectionManager(config);

        ClienteRepository clienteRepo = new ClienteRepository(cm);
        ProdutoRepository produtoRepo  = new ProdutoRepository(cm);
        PedidoRepository  pedidoRepo   = new PedidoRepository(cm);

        DataGeneratorService service = new DataGeneratorService(clienteRepo, produtoRepo, pedidoRepo);

        try {
            // Fase inicial: popula clientes e produtos uma vez
            service.cadastrarClientes(5);
            service.cadastrarProdutos();

            // Aguarda a réplica sincronizar os dados recém-inseridos antes de iniciar as leituras
            System.out.println("\n>>> Aguardando replicação sincronizar (2s)...");
            Thread.sleep(2000);

            int ciclo  = 0;
            int maxCiclos = config.getCycles(); // 0 = infinito

            System.out.println(">>> Iniciando ciclos de pedidos. Pressione Ctrl+C para parar.");

            while (maxCiclos == 0 || ciclo < maxCiclos) {
                ciclo++;
                System.out.printf("%n╔══════════ CICLO %-4d ══════════╗%n", ciclo);

                Pedido pedido = service.criarPedido();

                // UPDATE — atualiza status do pedido recém-criado no primário
                service.atualizarStatusPedido(pedido);

                // DELETE — a cada 5 ciclos, remove um cliente sem pedidos do primário
                if (ciclo % 5 == 0) {
                    service.removerClienteAntigo();
                }

                service.executarConsultas(pedido);

                System.out.printf("╚═════════ Fim Ciclo %-4d ════════╝%n", ciclo);

                if (maxCiclos == 0 || ciclo < maxCiclos) {
                    Thread.sleep(config.getCycleIntervalMs());
                }
            }

            System.out.println("\nAplicação finalizada após " + ciclo + " ciclo(s).");

        } catch (InterruptedException e) {
            System.out.println("\nAplicação interrompida pelo usuário.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("\nErro fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
