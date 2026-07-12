# Planner

Planner pessoal self-hosted, offline-first, com cliente Android nativo e backend leve.

## Estrutura

- `android/` — Kotlin + Jetpack Compose + Room + WorkManager.
- `server/` — Go + Chi + SQLite, REST/JSON e painel administrativo server-rendered.
- `deploy/` — Docker Compose, volume persistente e backup do SQLite.
- `docs/` — contexto, ADRs e arquitetura.

## Estado atual

O contrato de arquitetura está em [`docs/plans/2026-07-11-planner-architecture-design.md`](docs/plans/2026-07-11-planner-architecture-design.md). O backend self-hosted, o painel administrativo, o protocolo incremental e a operação de backup/restauração estão implementados. O cliente Android offline-first é o próximo slice.

## Desenvolvimento local

```sh
cp deploy/.env.example deploy/.env
# Edite deploy/.env e escolha credenciais fortes.
docker compose -f deploy/docker-compose.yml up --build
```

O servidor fica em `http://localhost:8080`. O primeiro admin é criado pelas variáveis `PLANNER_ADMIN_USERNAME` e `PLANNER_ADMIN_PASSWORD`.

HTTP é suportado para uso em rede local confiável. Para qualquer instalação acessível por outras redes, publique o serviço atrás de um proxy reverso com HTTPS; em HTTP, credenciais e dados trafegam sem criptografia.

## Backup e restauração

Configure no arquivo `deploy/.env`:

```dotenv
PLANNER_BACKUP_HOST_DIR=./backups
PLANNER_BACKUP_RETENTION_DAYS=14
```

Crie um backup consistente com o serviço em execução:

```sh
./deploy/backup.sh
```

O comando usa o backup online do SQLite, executa `integrity_check`, grava um arquivo `planner-<timestamp>.db` na pasta do host e remove cópias além da retenção configurada. Os backups permanecem no host e não são enviados aos dispositivos Android.

Para restaurar, escolha um arquivo existente na pasta configurada. A rotina cria antes uma cópia de segurança do estado atual, para o serviço, substitui o banco e só então inicia o health check novamente:

```sh
PLANNER_RESTORE_CONFIRM=yes ./deploy/restore.sh planner-20260711T120000.000000000Z.db
```

Antes de restaurar em produção, verifique espaço livre e preserve uma cópia externa da pasta de backups. Depois, autentique e execute um pull para confirmar Contas, Planners e estado sincronizado.

## Testes do backend

```sh
cd server
go test ./...

cd ../deploy
./smoke-test.sh
```
