package com.replicacao.db;

import com.replicacao.db.config.AppConfig;
import com.replicacao.db.connection.ConnectionManager;
import com.replicacao.db.model.Pedido;
import com.replicacao.db.repository.ClienteRepository;
import com.replicacao.db.repository.PedidoRepository;
import com.replicacao.db.repository.ProdutoRepository;
import com.replicacao.db.service.DataGeneratorService;

/**
 * PONTO DE ENTRADA DA APLICAÇÃO
 *
 * Esta classe inicia e controla o ciclo de vida da demonstração de replicação.
 * Ela não acessa o banco diretamente — delega tudo ao DataGeneratorService,
 * que por sua vez usa os repositories (ClienteRepository, ProdutoRepository,
 * PedidoRepository), que usam o ConnectionManager para separar leitura e escrita.
 *
 * FLUXO GERAL:
 *   1. AppConfig carrega o config.properties (hosts, portas, credenciais)
 *   2. ConnectionManager prepara as URLs JDBC do primário e das réplicas
 *   3. Fase inicial: insere clientes e produtos no PRIMÁRIO
 *   4. Aguarda 2 segundos para a réplica sincronizar (evitar lag na primeira leitura)
 *   5. Loop infinito: a cada ciclo executa INSERT + UPDATE + (DELETE a cada 5) + SELECTs
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Replicação de Banco de Dados - MySQL        ║");
        System.out.println("║   Gabriel Fillip e Leonardo Cassio            ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        // AppConfig lê o config.properties — primeiro busca o arquivo externo
        // ao lado do JAR (target/config.properties), depois o embutido no JAR.
        AppConfig config = new AppConfig();

        // ConnectionManager recebe a config e prepara as conexões JDBC.
        // Escrita → host primário | Leitura → réplicas em round-robin.
        ConnectionManager cm = new ConnectionManager(config);

        // Repositories recebem o ConnectionManager e usam getWriteConnection()
        // ou getReadConnection() conforme a operação (INSERT/UPDATE/DELETE vs SELECT).
        ClienteRepository clienteRepo = new ClienteRepository(cm);
        ProdutoRepository produtoRepo  = new ProdutoRepository(cm);
        PedidoRepository  pedidoRepo   = new PedidoRepository(cm);

        // DataGeneratorService orquestra a geração de dados aleatórios.
        // Ele não sabe se é primário ou réplica — isso é responsabilidade dos repositories.
        DataGeneratorService service = new DataGeneratorService(clienteRepo, produtoRepo, pedidoRepo);

        try {
            // ── FASE INICIAL ──────────────────────────────────────────────────────
            // Insere 5 clientes e 10 produtos no primário UMA VEZ ao iniciar.
            // Esses dados ficam disponíveis em todos os ciclos posteriores.
            service.cadastrarClientes(5);  // [WRITE → Primário] INSERT INTO cliente
            service.cadastrarProdutos();   // [WRITE → Primário] INSERT INTO produto

            // Pausa necessária porque a réplica MySQL tem um pequeno atraso (replication lag)
            // para receber os dados recém-inseridos no primário.
            // Sem esse sleep, a primeira leitura na réplica retornaria vazio.
            System.out.println("\n>>> Aguardando replicação sincronizar (2s)...");
            Thread.sleep(2000);

            // ── LOOP PRINCIPAL ────────────────────────────────────────────────────
            int ciclo    = 0;
            int maxCiclos = config.getCycles(); // 0 = roda infinitamente

            System.out.println(">>> Iniciando ciclos de pedidos. Pressione Ctrl+C para parar.");

            while (maxCiclos == 0 || ciclo < maxCiclos) {
                ciclo++;
                System.out.printf("%n╔══════════ CICLO %-4d ══════════╗%n", ciclo);

                // [WRITE → Primário] INSERT pedido + pedido_item (transação única)
                // Internamente, criarPedido() lê clientes e produtos da RÉPLICA antes de inserir.
                Pedido pedido = service.criarPedido();

                // [WRITE → Primário] UPDATE pedido SET status = próximo_status
                // Demonstra que atualizações também vão ao primário e se replicam.
                service.atualizarStatusPedido(pedido);

                // [WRITE → Primário] DELETE FROM cliente WHERE id = ?
                // Executado a cada 5 ciclos para demonstrar DELETE com replicação.
                // Se todos os clientes tiverem pedidos, o DELETE é pulado com mensagem [SKIP].
                if (ciclo % 5 == 0) {
                    service.removerClienteAntigo();
                }

                // [READ → Réplica] Série de SELECTs que demonstram a leitura via réplica:
                //   - buscarPorId (JOIN pedido + cliente)
                //   - buscarItensDoPedido (JOIN pedido_item + produto)
                //   - historicoPorCliente (últimos 5 pedidos)
                //   - exibirRelatorioAgregado (COUNT / AVG / SUM)
                service.executarConsultas(pedido);

                System.out.printf("╚═════════ Fim Ciclo %-4d ════════╝%n", ciclo);

                // Intervalo configurável entre ciclos (padrão: 3000ms = 3 segundos)
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
