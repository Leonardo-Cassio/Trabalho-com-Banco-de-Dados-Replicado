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
 * Serviço de geração de dados — coordena todas as operações do ciclo automático.
 *
 * RESPONSABILIDADE:
 *   Gerar dados realistas e aleatórios, demonstrando em cada operação
 *   para qual host ela é roteada (primário ou réplica).
 *
 * OPERAÇÕES IMPLEMENTADAS:
 *   INSERT  — cadastrarClientes(), cadastrarProdutos(), criarPedido()   → PRIMÁRIO
 *   UPDATE  — atualizarStatusPedido()                                   → PRIMÁRIO
 *   DELETE  — removerClienteAntigo()                                    → PRIMÁRIO
 *   SELECT  — executarConsultas() (4.1 a 4.4)                          → RÉPLICA
 *
 * DADOS FIXOS vs ALEATÓRIOS:
 *   Nomes e produtos são arrays fixos — garantem dados legíveis e realistas.
 *   A aleatoriedade vem da escolha aleatória dentro desses arrays (Random).
 */
public class DataGeneratorService {

    // Identificador gravado no campo criado_por de todas as tabelas.
    // Limitado a 15 chars para respeitar o varchar(30) do schema do professor.
    private static final String CRIADO_POR = "Fillip e Cassio"; // max varchar(30)

    // Pool de nomes usados para gerar clientes aleatórios
    private static final String[] NOMES = {
            "Ana Silva", "Bruno Costa", "Carla Souza", "Diego Pereira", "Eduarda Lima",
            "Fábio Alves", "Gabriela Santos", "Henrique Rocha", "Isabela Martins", "João Ferreira",
            "Kamila Nunes", "Lucas Gomes", "Marina Oliveira", "Nicolas Teixeira", "Olivia Cardoso"
    };

    // Catálogo fixo de produtos: [descrição, categoria, preço, estoque]
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

    // Possíveis status de pedido — um é escolhido aleatoriamente no INSERT
    private static final String[] STATUSES = {"PENDENTE", "APROVADO", "ENVIADO", "FINALIZADO", "CANCELADO"};

    private final ClienteRepository clienteRepo;
    private final ProdutoRepository produtoRepo;
    private final PedidoRepository  pedidoRepo;
    private final Random random = new Random();

    // Sufixo único por execução da JVM — garante emails distintos em runs consecutivos
    // (evita violação do UNIQUE(email) ao reiniciar a aplicação)
    private final long runId = System.currentTimeMillis();

    public DataGeneratorService(ClienteRepository clienteRepo,
                                ProdutoRepository produtoRepo,
                                PedidoRepository pedidoRepo) {
        this.clienteRepo = clienteRepo;
        this.produtoRepo  = produtoRepo;
        this.pedidoRepo   = pedidoRepo;
    }

    // ------------------------------------------------------------------
    // 1. Cadastro automático de clientes — INSERT no PRIMÁRIO
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

            // Evita email duplicado dentro do mesmo lote (antes de tentar o INSERT)
            if (emailsUsados.contains(email)) continue;
            emailsUsados.add(email);

            Cliente c = new Cliente(nome, email, CRIADO_POR);
            clienteRepo.inserir(c); // → getWriteConnection() → PRIMÁRIO
            System.out.printf("  [+] Cliente %-3d | Nome: %-25s | E-mail: %s%n",
                    c.getId(), c.getNome(), c.getEmail());
            inseridos++;
        }
    }

    // ------------------------------------------------------------------
    // 2. Cadastro automático de produtos — INSERT no PRIMÁRIO
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
            produtoRepo.inserir(p); // → getWriteConnection() → PRIMÁRIO
            System.out.printf("  [+] Produto %-3d | %-30s | R$ %8.2f | Estoque: %d%n",
                    p.getId(), p.getDescricao(), p.getValor(), p.getEstoque());
        }
    }

    // ------------------------------------------------------------------
    // 3. Criação de pedido — INSERT no PRIMÁRIO, SELECT na RÉPLICA
    // ------------------------------------------------------------------
    public Pedido criarPedido() throws SQLException, InterruptedException {
        // Busca clientes e produtos na RÉPLICA para montar o pedido.
        // Tenta até 5 vezes caso a réplica ainda esteja com lag após o INSERT inicial.
        List<Cliente> clientes = new ArrayList<>();
        List<Produto> produtos  = new ArrayList<>();
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            clientes = clienteRepo.listarTodos(); // → getReadConnection() → RÉPLICA
            produtos  = produtoRepo.listarTodos(); // → getReadConnection() → RÉPLICA
            if (!clientes.isEmpty() && !produtos.isEmpty()) break;
            System.out.println("  [REPLICA LAG] Réplica ainda sincronizando, aguardando 1s... (tentativa " + tentativa + "/5)");
            Thread.sleep(1000);
        }
        if (clientes.isEmpty()) throw new IllegalStateException("Nenhum cliente cadastrado.");
        if (produtos.isEmpty())  throw new IllegalStateException("Nenhum produto cadastrado.");

        // Escolhe cliente e produtos aleatoriamente
        Cliente clienteSelecionado = clientes.get(random.nextInt(clientes.size()));
        int numItens = 1 + random.nextInt(Math.min(3, produtos.size())); // 1 a 3 itens
        Collections.shuffle(produtos, random);
        List<Produto> produtosSelecionados = produtos.subList(0, numItens);

        // Monta os itens e calcula o valor total do pedido
        List<PedidoItem> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Produto prod : produtosSelecionados) {
            int qtd = 1 + random.nextInt(4); // 1 a 4 unidades por item
            BigDecimal subtotal = prod.getValor().multiply(BigDecimal.valueOf(qtd));
            total = total.add(subtotal);
            itens.add(new PedidoItem(0, prod.getId(), qtd, prod.getValor()));
        }

        String status = STATUSES[random.nextInt(STATUSES.length)];
        Pedido pedido = new Pedido(clienteSelecionado.getId(), total, status, CRIADO_POR);
        pedido.setItens(itens);

        // Grava pedido e itens no PRIMÁRIO em uma única transação
        pedidoRepo.inserirComItens(pedido); // → getWriteConnection() → PRIMÁRIO

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
    // 4. Consultas na RÉPLICA (itens 4.1 a 4.4 do enunciado)
    // ------------------------------------------------------------------
    public void executarConsultas(Pedido pedido) throws SQLException {
        System.out.println("\n========================================");
        System.out.println(" CONSULTAS  [READ → Réplica]");
        System.out.println("========================================");

        // 4.1 — SELECT pedido por ID com JOIN no cliente → RÉPLICA
        System.out.println("\n  4.1 Pedido por ID:");
        pedidoRepo.buscarPorId(pedido.getId()).ifPresent(p ->
            System.out.printf("    Pedido %d | Status: %-12s | Valor Total: R$ %.2f%n",
                    p.getId(), p.getStatus(), p.getValorTotal())
        );

        // 4.2 — SELECT itens do pedido com JOIN no produto → RÉPLICA
        System.out.println("\n  4.2 Itens do Pedido:");
        List<PedidoItem> itens = pedidoRepo.buscarItensDoPedido(pedido.getId());
        for (PedidoItem item : itens) {
            System.out.printf("    PedidoItem %-3d | Produto ID: %-3d | Qtd: %d | R$ %.2f%n",
                    item.getId(), item.getProdutoId(), item.getQuantidade(), item.getValorUnitario());
        }

        // 4.3 — SELECT últimos 5 pedidos do cliente → RÉPLICA
        System.out.println("\n  4.3 Histórico do Cliente (últimos 5 pedidos):");
        List<Pedido> historico = pedidoRepo.historicoPorCliente(pedido.getClienteId(), 5);
        for (Pedido p : historico) {
            System.out.printf("    Pedido %-4d | R$ %.2f%n", p.getId(), p.getValorTotal());
        }

        // 4.4 — SELECT com COUNT, AVG e SUM → RÉPLICA
        System.out.println("\n  4.4 Relatório Agregado (SUM / COUNT / AVG):");
        pedidoRepo.exibirRelatorioAgregado();
    }

    // ------------------------------------------------------------------
    // 5. UPDATE no PRIMÁRIO — avança o status do pedido
    // ------------------------------------------------------------------
    public void atualizarStatusPedido(Pedido pedido) throws SQLException {
        // Progressão de status: PENDENTE → APROVADO → ENVIADO → FINALIZADO
        // Se o status já for CANCELADO ou FINALIZADO, vai direto para FINALIZADO.
        String[] progresso = {"PENDENTE", "APROVADO", "ENVIADO", "FINALIZADO"};
        String statusAtual = pedido.getStatus();
        String novoStatus  = "FINALIZADO"; // padrão caso não encontre na progressão

        for (int i = 0; i < progresso.length - 1; i++) {
            if (progresso[i].equals(statusAtual)) {
                novoStatus = progresso[i + 1];
                break;
            }
        }

        boolean atualizado = pedidoRepo.atualizarStatus(pedido.getId(), novoStatus); // → PRIMÁRIO

        System.out.println("\n========================================");
        System.out.println(" ATUALIZAÇÃO DE PEDIDO  [WRITE → Primário]");
        System.out.println("========================================");
        System.out.printf("  UPDATE pedido SET status = '%s' WHERE id = %d%n", novoStatus, pedido.getId());
        System.out.printf("  Status anterior : %s%n", statusAtual);
        System.out.printf("  Status novo     : %s%n", novoStatus);
        System.out.printf("  Linhas afetadas : %d%n", atualizado ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // 6. DELETE no PRIMÁRIO — remove um cliente sem pedidos a cada 5 ciclos
    // ------------------------------------------------------------------
    public void removerClienteAntigo() throws SQLException {
        System.out.println("\n========================================");
        System.out.println(" REMOÇÃO DE CLIENTE  [WRITE → Primário]");
        System.out.println("========================================");

        // Busca na RÉPLICA um cliente que não tenha pedidos (safe para deletar)
        var candidato = clienteRepo.buscarClienteSemPedidos(); // → RÉPLICA

        if (candidato.isEmpty()) {
            // Todos os clientes têm pedidos — deleting any would violate the foreign key.
            // Solução: insere um cliente temporário no PRIMÁRIO apenas para demonstrar o DELETE.
            String nome  = "Cliente Temporário";
            String email = "temp." + System.currentTimeMillis() + "@email.com";
            Cliente temp = new Cliente(nome, email, CRIADO_POR);
            clienteRepo.inserir(temp); // → PRIMÁRIO
            System.out.printf("  [INSERT] Nenhum cliente sem pedidos — criado temporário (ID %d)%n", temp.getId());
            candidato = java.util.Optional.of(temp);
        }

        Cliente alvo = candidato.get();
        boolean deletado = clienteRepo.deletar(alvo.getId()); // → PRIMÁRIO

        System.out.printf("  DELETE FROM cliente WHERE id = %d%n", alvo.getId());
        System.out.printf("  Cliente removido: %s (ID %d)%n", alvo.getNome(), alvo.getId());
        System.out.printf("  Linhas afetadas : %d%n", deletado ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // Utilitários
    // ------------------------------------------------------------------

    /**
     * Gera um email único combinando o nome do cliente com o runId da JVM.
     * O runId (timestamp em ms) garante que emails não se repitam entre execuções,
     * evitando violação da constraint UNIQUE(email) da tabela cliente.
     */
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
