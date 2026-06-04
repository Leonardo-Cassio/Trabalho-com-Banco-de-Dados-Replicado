# ============================================================
# testar-local.ps1
# Sobe o ambiente Docker, verifica a replicação e instrui
# como rodar a aplicação Java e a API REST.
# ============================================================

# Não usar "Stop" globalmente — comandos Docker emitem warnings no stderr
# que o PS 5.1 trata como erros mesmo quando o exit code é 0.
$ErrorActionPreference = "Continue"

function Write-Step($msg) {
    Write-Host ""
    Write-Host "======================================" -ForegroundColor Cyan
    Write-Host "  $msg" -ForegroundColor Cyan
    Write-Host "======================================" -ForegroundColor Cyan
}

# ── 1. Subir containers ──────────────────────────────────────
Write-Step "1. Subindo containers Docker (primario + replica)"
docker compose up -d --build
if ($LASTEXITCODE -ne 0) {
    Write-Host "Erro ao subir os containers Docker. Verifique se o Docker Desktop esta rodando." -ForegroundColor Red
    exit 1
}

# ── 2. Aguardar setup da replicação ─────────────────────────
Write-Step "2. Aguardando configuracao da replicacao"
Write-Host "  (isso pode levar ~30 segundos na primeira vez)"
Start-Sleep -Seconds 20

# Exibe logs do container de setup (stdout apenas — sem 2>&1 para evitar NativeCommandError)
Write-Host "  Logs do replica-setup:"
docker logs replica-setup
Write-Host ""

# ── 3. Verificar replicação ──────────────────────────────────
Write-Step "3. Status da replicacao na replica"

# Captura stdout do mysql; o warning de senha vai para stderr e é ignorado
# pelo PS5.1 desde que não usemos 2>&1
$statusOutput = docker exec mysql-replica mysql -uroot -proot -e "SHOW REPLICA STATUS\G"
$statusOutput | Select-String -Pattern "Running|Behind|Error"

# ── 4. Testar insert no primário e ler na réplica ────────────
Write-Step "4. Teste rapido: escreve no primario, le na replica"

Write-Host "  [WRITE] Inserindo cliente teste no primario (porta 3306)..."
docker exec mysql-primary mysql -uroot -proot "aula-db" -e "INSERT IGNORE INTO cliente (nome, email, criado_por) VALUES ('Cliente Teste', 'teste@teste.com', 'Script de Teste');"

Write-Host "  Aguardando replicacao propagar (2s)..."
Start-Sleep -Seconds 2

Write-Host "  [READ]  Lendo na replica (porta 3307)..."
$resultado = docker exec mysql-replica mysql -uroot -proot "aula-db" -e "SELECT id, nome, email FROM cliente;"

if ($resultado -match "Cliente Teste") {
    Write-Host "  REPLICACAO OK - dado apareceu na replica!" -ForegroundColor Green
} else {
    Write-Host "  ATENCAO - dado nao apareceu ainda. Tente rodar novamente em alguns segundos." -ForegroundColor Yellow
    Write-Host $resultado
}

# ── 5. Instruções finais ─────────────────────────────────────
Write-Step "5. Ambiente pronto! Proximos passos"

Write-Host ""
Write-Host "  Conexoes ja configuradas (config.properties e .env):" -ForegroundColor White
Write-Host "    Primario  -> localhost:3306  (usuario: root  senha: root)" -ForegroundColor White
Write-Host "    Replica   -> localhost:3307  (usuario: root  senha: root)" -ForegroundColor White
Write-Host "    Database  -> aula-db" -ForegroundColor White
Write-Host ""
Write-Host "  Para rodar a aplicacao Java (Terminal 1):" -ForegroundColor Yellow
Write-Host "    cd java-app"
Write-Host "    mvn clean package -q"
Write-Host "    java -cp `"target/db-replicacao.jar;target/libs/*`" com.replicacao.db.Main"
Write-Host ""
Write-Host "  Para rodar a API REST (Terminal 2):" -ForegroundColor Yellow
Write-Host "    cd api-rest"
Write-Host "    npm install"
Write-Host "    npm start"
Write-Host ""
Write-Host "  Para testar a API (Terminal 3):" -ForegroundColor Yellow
Write-Host "    curl http://localhost:3000/pedidos/1"
Write-Host "    curl http://localhost:3000/clientes/1/pedidos"
Write-Host "    curl http://localhost:3000/produtos/baixo-estoque"
Write-Host "    curl http://localhost:3000/relatorios/vendas"
Write-Host ""
Write-Host "  Para parar tudo:" -ForegroundColor Yellow
Write-Host "    docker compose down"
Write-Host ""
Write-Host "  Para apagar os dados e comecar do zero:" -ForegroundColor Yellow
Write-Host "    docker compose down -v"
Write-Host ""
