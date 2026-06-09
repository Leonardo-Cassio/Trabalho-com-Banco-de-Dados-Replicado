package com.replicacao.db.service;

import com.replicacao.db.model.Cliente;
import com.replicacao.db.model.Pedido;
import com.replicacao.db.model.PedidoItem;
import com.replicacao.db.model.Produto;
import com.replicacao.db.repository.ClienteRepository;
import com.replicacao.db.repository.PedidoRepository;
import com.replicacao.db.repository.ProdutoRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

/**
 * Serviço responsável por gerar e persistir dados aleatórios de demonstração.
 * Toda escrita vai ao primário; toda leitura vai à réplica.
 */
public class DataGeneratorService {

    private static final String CRIADO_POR = "Gabriel Fillip e Leonardo Cassio";

    private static final String[] NOMES = {
            "Ana Silva", "Bruno Costa", "Carla Souza", "Diego Pereira", "Eduarda Lima",
            "Fábio Alves", "Gabriela Santos", "Henrique Rocha", "Isabela Martins", "João Ferreira",
            "Kamila Nunes", "Lucas Gomes", "Marina Oliveira", "Nicolas Teixeira", "Olivia Cardoso"
    };

    private static final String[][] PRODUTOS = {
            {"Notebook Dell Inspiron",   "Informática",  "3499.90", "15"},
            {"Mouse Logitech MX Master", "Informática",   "349.90", "50"},
            {"Teclado Mecânico HyperX",  "Informática",   "599.90", "30"},
            {"Monitor LG 27\"",          "Informática",  "1799.90",  "8"},
            {"Headset Sony WH-1000XM5", "Áudio",         "1199.90", "20"},
            {"Webcam Logitech C920",     "Informática",   "449.90", "25"},
            {"SSD Samsung 1TB",          "Armazenamento",  "399.90", "40"},
            {"HD Externo Seagate 2TB",   "Armazenamento",  "279.90", "35"},
            {"Tablet Samsung Galaxy",    "Mobile",       "1599.90", "12"},
            {"Cadeira Gamer DXRacer",    "Mobiliário",   "2199.90",  "6"}
    };

    private static final String[] STATUSES = {"PENDENTE", "APROVADO", "ENVIADO", "FINALIZADO", "CANCELADO"};

    private final ClienteRepository clienteRepo;
    private final ProdutoRepository produtoRepo;
    private final PedidoRepository  pedidoRepo;
    private final Random random = new Random();

    private final List<Cliente> clientesCadastrados = new ArrayList<>();
    private final List<Produto> produtosCadastrados = new ArrayList<>();

    // Sufixo único por execução — garante emails distintos em runs consecutivos
    private final long runId = System.currentTimeMillis();

    public DataGeneratorService(ClienteRepository clienteRepo,
                                ProdutoRepository produtoRepo,
                                PedidoRepository pedidoRepo) {
        this.clienteRepo = clienteRepo;
        this.produtoRepo  = produtoRepo;
        this.pedidoRepo   = pedidoRepo;
    }

    // ------------------------------------------------------------------
    // 1. Cadastro automático de clientes
    // ------------------------------------------------------------------
    public void cadastrarClientes(int quantidade) throws SQLException {
        System.out.println("\n========================================");
        System.out.println(" CADASTRO DE CLIENTES  [WRITE → Primário]");
        System.out.println("========================================");

        Set<String> emailsUsados = new HashSet<>();
        int inseridos = 0;

        while (inseridos < quantidade) {
            String nome  = NOMES[random.nextInt(NOMES.length)];
            String email = gerarEmail(nome, inseridos);

            if (emailsUsados.contains(email)) continue;
            emailsUsados.add(email);

            Cliente c = new Cliente(nome, email, CRIADO_POR);
            clienteRepo.inserir(c);
            clientesCadastrados.add(c);

            System.out.printf("  [+] Cliente %-3d | Nome: %-25s | E-mail: %s%n",
                    c.getId(), c.getNome(), c.getEmail());
            inseridos++;
        }
    }

    // ------------------------------------------------------------------
    // 2. Cadastro automático de produtos
    // ------------------------------------------------------------------
    public void cadastrarProdutos() throws SQLException {
        System.out.println("\n========================================");
        System.out.println(" CADASTRO DE PRODUTOS  [WRITE → Primário]");
        System.out.println("========================================");

        for (String[] dados : PRODUTOS) {
            Produto p = new Produto(
                    dados[0], dados[1],
                    new BigDecimal(dados[2]),
                    Integer.parseInt(dados[3]),
                    CRIADO_POR
            );
            produtoRepo.inserir(p);
            produtosCadastrados.add(p);

            System.out.printf("  [+] Produto %-3d | %-30s | R$ %8.2f | Estoque: %d%n",
                    p.getId(), p.getDescricao(), p.getValor(), p.getEstoque());
        }
    }

    // ------------------------------------------------------------------
    // 3. Criação automática de pedidos (1 por ciclo)
    // ------------------------------------------------------------------
    public Pedido criarPedido() throws SQLException, InterruptedException {
        // Seleciona um cliente já existente (via réplica)
        // Tenta até 5 vezes caso a réplica ainda esteja com lag
        List<Cliente> clientes = new ArrayList<>();
        List<Produto> produtos  = new ArrayList<>();
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            clientes = clienteRepo.listarTodos();
            produtos  = produtoRepo.listarTodos();
            if (!clientes.isEmpty() && !produtos.isEmpty()) break;
            System.out.println("  [REPLICA LAG] Réplica ainda sincronizando, aguardando 1s... (tentativa " + tentativa + "/5)");
            Thread.sleep(1000);
        }
        if (clientes.isEmpty()) throw new IllegalStateException("Nenhum cliente cadastrado.");
        if (produtos.isEmpty())  throw new IllegalStateException("Nenhum produto cadastrado.");

        Cliente clienteSelecionado = clientes.get(random.nextInt(clientes.size()));
        int numItens = 1 + random.nextInt(Math.min(3, produtos.size()));
        Collections.shuffle(produtos, random);
        List<Produto> produtosSelecionados = produtos.subList(0, numItens);

        // Monta pedido
        List<PedidoItem> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Produto prod : produtosSelecionados) {
            int qtd = 1 + random.nextInt(4);
            BigDecimal subtotal = prod.getValor().multiply(BigDecimal.valueOf(qtd));
            total = total.add(subtotal);
            itens.add(new PedidoItem(0, prod.getId(), qtd, prod.getValor()));
        }

        String status = STATUSES[random.nextInt(STATUSES.length)];
        Pedido pedido = new Pedido(clienteSelecionado.getId(), total, status, CRIADO_POR);
        pedido.setItens(itens);

        // Persiste no primário (transação única)
        pedidoRepo.inserirComItens(pedido);

        System.out.println("\n========================================");
        System.out.println(" PEDIDO CRIADO  [WRITE → Primário]");
        System.out.println("========================================");
        System.out.printf("  ID do Pedido  : %d%n",       pedido.getId());
        System.out.printf("  Cliente       : %s (ID %d)%n", clienteSelecionado.getNome(), clienteSelecionado.getId());
        System.out.printf("  Valor Total   : R$ %.2f%n",  pedido.getValorTotal());
        System.out.printf("  Total de Itens: %d%n",       itens.size());
        System.out.printf("  Status        : %s%n",        pedido.getStatus());

        return pedido;
    }

    // ------------------------------------------------------------------
    // 4. Consultas usando SOMENTE a réplica
    // ------------------------------------------------------------------
    public void executarConsultas(Pedido pedido) throws SQLException {
        System.out.println("\n========================================");
        System.out.println(" CONSULTAS  [READ → Réplica]");
        System.out.println("========================================");

        // 4.1 Buscar pedido por ID
        System.out.println("\n  4.1 Pedido por ID:");
        pedidoRepo.buscarPorId(pedido.getId()).ifPresent(p -> {
            System.out.printf("    Pedido %d | Status: %-12s | Valor Total: R$ %.2f%n",
                    p.getId(), p.getStatus(), p.getValorTotal());
        });

        // 4.2 Itens do pedido
        System.out.println("\n  4.2 Itens do Pedido:");
        List<PedidoItem> itens = pedidoRepo.buscarItensDoPedido(pedido.getId());
        for (PedidoItem item : itens) {
            System.out.printf("    PedidoItem %-3d | Produto ID: %-3d | Qtd: %d | R$ %.2f%n",
                    item.getId(), item.getProdutoId(), item.getQuantidade(), item.getValorUnitario());
        }

        // 4.3 Histórico do cliente (últimos 5 pedidos)
        System.out.println("\n  4.3 Histórico do Cliente (últimos 5 pedidos):");
        List<Pedido> historico = pedidoRepo.historicoPorCliente(pedido.getClienteId(), 5);
        for (Pedido p : historico) {
            System.out.printf("    Pedido %-4d | R$ %.2f%n", p.getId(), p.getValorTotal());
        }

        // 4.4 Relatório agregado
        System.out.println("\n  4.4 Relatório Agregado (SUM / COUNT / AVG):");
        pedidoRepo.exibirRelatorioAgregado();
    }

    // ------------------------------------------------------------------
    // 5. UPDATE — atualiza o status do pedido recém-criado no primário
    // ------------------------------------------------------------------
    public void atualizarStatusPedido(Pedido pedido) throws SQLException {
        // Avança o status para o próximo estágio de forma determinística
        String[] progresso = {"PENDENTE", "APROVADO", "ENVIADO", "FINALIZADO"};
        String statusAtual = pedido.getStatus();
        String novoStatus  = "FINALIZADO"; // padrão: finaliza qualquer status

        for (int i = 0; i < progresso.length - 1; i++) {
            if (progresso[i].equals(statusAtual)) {
                novoStatus = progresso[i + 1];
                break;
            }
        }

        boolean atualizado = pedidoRepo.atualizarStatus(pedido.getId(), novoStatus);

        System.out.println("\n========================================");
        System.out.println(" ATUALIZAÇÃO DE PEDIDO  [WRITE → Primário]");
        System.out.println("========================================");
        System.out.printf("  UPDATE pedido SET status = '%s' WHERE id = %d%n", novoStatus, pedido.getId());
        System.out.printf("  Status anterior : %s%n", statusAtual);
        System.out.printf("  Status novo     : %s%n", novoStatus);
        System.out.printf("  Linhas afetadas : %d%n", atualizado ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // 6. DELETE — remove um cliente sem pedidos a cada N ciclos
    // ------------------------------------------------------------------
    public void removerClienteAntigo() throws SQLException {
        System.out.println("\n========================================");
        System.out.println(" REMOÇÃO DE CLIENTE  [WRITE → Primário]");
        System.out.println("========================================");

        var candidato = clienteRepo.buscarClienteSemPedidos();

        if (candidato.isEmpty()) {
            System.out.println("  [SKIP] Todos os clientes possuem pedidos — DELETE ignorado neste ciclo.");
            return;
        }

        Cliente alvo = candidato.get();
        boolean deletado = clienteRepo.deletar(alvo.getId());

        System.out.printf("  DELETE FROM cliente WHERE id = %d%n", alvo.getId());
        System.out.printf("  Cliente removido: %s (ID %d)%n", alvo.getNome(), alvo.getId());
        System.out.printf("  Linhas afetadas : %d%n", deletado ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // Utilitários
    // ------------------------------------------------------------------
    private String gerarEmail(String nome, int sufixo) {
        String base = nome.toLowerCase()
                .replace(" ", ".")
                .replace("á", "a").replace("ã", "a").replace("â", "a")
                .replace("é", "e").replace("ê", "e")
                .replace("í", "i")
                .replace("ó", "o").replace("ô", "o")
                .replace("ú", "u").replace("ü", "u")
                .replace("ç", "c")
                .replace("ñ", "n");
        return base + "." + runId + sufixo + "@email.com";
    }
}
