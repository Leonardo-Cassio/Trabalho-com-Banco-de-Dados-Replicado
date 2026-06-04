#!/bin/bash
# Configura replicação MySQL: sincroniza dados do primário e inicia replication slave.
# Todos os comandos especificam o host explicitamente (-h) pois este container
# não roda um servidor MySQL — é apenas um client de setup.

set -e

echo ">>> Aguardando primário ficar disponível..."
until mysql -h mysql-primary -uroot -proot -e "SELECT 1" > /dev/null 2>&1; do
  sleep 2
done
echo ">>> Primário disponível."

echo ">>> Aguardando réplica ficar disponível..."
until mysql -h mysql-replica -uroot -proot -e "SELECT 1" > /dev/null 2>&1; do
  sleep 2
done
echo ">>> Réplica disponível."

# Trava o primário para obter posição consistente do binlog
echo ">>> Bloqueando writes no primário para dump consistente..."
mysql -h mysql-primary -uroot -proot -e "FLUSH TABLES WITH READ LOCK;" > /dev/null 2>&1

STATUS=$(mysql -h mysql-primary -uroot -proot -e "SHOW MASTER STATUS\G" 2>/dev/null)
BINLOG_FILE=$(echo "$STATUS" | grep "File:"     | awk '{print $2}')
BINLOG_POS=$(echo  "$STATUS" | grep "Position:" | awk '{print $2}')

echo "    Arquivo binlog : $BINLOG_FILE"
echo "    Posição        : $BINLOG_POS"

# Copia todos os bancos do primário para a réplica
echo ">>> Copiando dados do primário para a réplica via mysqldump..."
mysqldump -h mysql-primary -uroot -proot \
  --single-transaction --set-gtid-purged=OFF \
  --all-databases 2>/dev/null \
  | mysql -h mysql-replica -uroot -proot 2>/dev/null

echo ">>> Dump concluído."

# Libera o lock no primário
mysql -h mysql-primary -uroot -proot -e "UNLOCK TABLES;" > /dev/null 2>&1

# Configura e inicia a replicação a partir da posição capturada
echo ">>> Configurando replication slave na réplica..."
mysql -h mysql-replica -uroot -proot 2>/dev/null <<SQL
  STOP REPLICA;
  CHANGE REPLICATION SOURCE TO
    SOURCE_HOST      = 'mysql-primary',
    SOURCE_USER      = 'replicator',
    SOURCE_PASSWORD  = 'replicator_pass',
    SOURCE_LOG_FILE  = '$BINLOG_FILE',
    SOURCE_LOG_POS   =  $BINLOG_POS;
  START REPLICA;
SQL

echo ">>> Replicação configurada com sucesso!"
echo ""
echo "=== Status da Replicação ==="
mysql -h mysql-replica -uroot -proot -e "SHOW REPLICA STATUS\G" 2>/dev/null | grep -E "Running|Error|Behind"
