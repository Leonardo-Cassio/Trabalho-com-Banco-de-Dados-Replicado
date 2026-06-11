# ============================================================
# Acesso rápido ao banco de dados via terminal
# ============================================================
# Execute: .\acessar-banco.ps1
# ============================================================

Write-Host ""
Write-Host "========================================"
Write-Host "  Acesso ao Banco de Dados"
Write-Host "========================================"
Write-Host ""
Write-Host "  [1] Acessar Primario (porta 3306)"
Write-Host "  [2] Acessar Replica  (porta 3307)"
Write-Host "  [3] Ver status da replicacao"
Write-Host "  [4] Ver ultimos 10 pedidos (replica)"
Write-Host ""

$opcao = Read-Host "Escolha uma opcao"

switch ($opcao) {
    "1" {
        Write-Host ""
        Write-Host "Conectando ao Primario (porta 3306)..."
        Write-Host "Dica: SELECT * FROM pedido ORDER BY id DESC LIMIT 10;"
        Write-Host ""
        docker exec -it mysql-primary mysql -uroot -proot aula-db
    }
    "2" {
        Write-Host ""
        Write-Host "Conectando a Replica (porta 3307)..."
        Write-Host "Dica: SELECT * FROM pedido ORDER BY id DESC LIMIT 10;"
        Write-Host ""
        docker exec -it mysql-replica mysql -uroot -proot aula-db
    }
    "3" {
        Write-Host ""
        Write-Host "Status da replicacao:"
        Write-Host ""
        docker exec mysql-replica mysql -uroot -proot -e "SHOW REPLICA STATUS\G"
    }
    "4" {
        Write-Host ""
        Write-Host "Ultimos 10 pedidos na replica:"
        Write-Host ""
        docker exec mysql-replica mysql -uroot -proot aula-db -e "SELECT id, cliente_id, valor_total, status, criado_em FROM pedido ORDER BY id DESC LIMIT 10;"
    }
    default {
        Write-Host "Opcao invalida."
    }
}
