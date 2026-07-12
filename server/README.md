# Backend do Planner

Serviço self-hosted em Go, Chi e SQLite. O processo aplica o schema, cria a primeira Conta administradora de forma idempotente e atende a API JSON e o painel HTML no mesmo endereço.

## Executar localmente

```sh
export PLANNER_DB_PATH="$PWD/planner.db"
export PLANNER_ADMIN_USERNAME=owner
export PLANNER_ADMIN_PASSWORD='escolha-uma-senha-forte'
go run ./cmd/server
```

Variáveis:

- `PLANNER_HTTP_ADDR`: endereço HTTP; padrão `:8080`.
- `PLANNER_DB_PATH`: caminho do SQLite; no container, `/data/planner.db`.
- `PLANNER_ADMIN_USERNAME`: nome obrigatório da primeira Conta administradora.
- `PLANNER_ADMIN_PASSWORD`: senha obrigatória da primeira Conta administradora.

As credenciais de bootstrap só são usadas quando o banco ainda não possui Contas. Reiniciar o serviço ou alterar as variáveis não substitui a Conta existente.

## API v1

Autenticação:

```text
POST /api/v1/auth/login
POST /api/v1/auth/change-password
POST /api/v1/auth/logout
GET  /api/v1/me
```

O login retorna `token`, `account` e `planner`. Contas criadas ou com senha redefinida recebem `must_change_password: true`; até a troca, `/me` e sync respondem `password_change_required`. Uma Conta desativada recebe `account_disabled` no próximo login ou endpoint autenticado.

Sincronização:

```text
POST /api/v1/sync/push
GET  /api/v1/sync/pull?cursor=0&limit=100
```

Cada operação de push possui `operation_id`, `entity_type`, `entity_id`, `kind` e `payload`. Tipos aceitos: `task`, `routine`, `routine_occurrence` e `account_settings`. O resultado de cada operação é `accepted`, `duplicate` ou `rejected`; um retry com o mesmo ID dentro da mesma Conta não gera outra mudança.

O pull devolve `changes`, `next_cursor` e `has_more`. Cursores e operações são isolados por Conta. Alterações concorrentes ganham versões crescentes do servidor, e a última versão aceita é a mudança final observável.

## Painel administrativo

Abra `/admin/login`. Somente a Conta administradora pode:

- listar metadados operacionais das Contas;
- criar Conta com senha temporária e Fuso da Conta;
- ativar ou desativar acesso;
- redefinir uma senha, invalidando sessões anteriores;
- encerrar a sessão administrativa.

O painel não consulta nem renderiza conteúdo de Planners.

## Verificação

```sh
go test ./...
go test -race ./...
go vet ./...
```

O smoke test Docker está em `../deploy/smoke-test.sh` e cobre health, bootstrap idempotente, volume persistente, backup e restauração.
