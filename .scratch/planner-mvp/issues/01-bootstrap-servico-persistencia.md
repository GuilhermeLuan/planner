# Bootstrap do serviço e persistência da instalação

Status: complete

## What to build

Entregar uma instalação self-hosted mínima e verificável do Planner. O serviço deve iniciar com Go/Chi e SQLite, aplicar o schema necessário para Contas, Planners, sessões e sincronização, expor um health check e criar de forma idempotente a primeira Conta administradora a partir da configuração da instalação. O pacote Docker Compose deve manter o banco em volume persistente e documentar a recomendação de HTTPS para instalações acessíveis pela rede.

O slice atravessa serviço, persistência, configuração de deploy e smoke tests operacionais. Ele não precisa entregar o painel administrativo nem o cliente Android.

## Acceptance criteria

- [x] Uma instalação nova sobe com Docker Compose e responde `200` no health check.
- [x] A primeira inicialização cria a Conta administradora e seu Planner usando as credenciais configuradas, sem recriá-la em reinicializações posteriores.
- [x] Migrações são aplicadas automaticamente e preservam integridade e isolamento por Conta.
- [x] Recriar o container mantendo o volume preserva o banco e os dados de bootstrap.
- [x] A configuração e a documentação deixam claro o suporte a HTTP local e a recomendação de HTTPS para acesso em rede.
- [x] Existe um smoke test automatizado para inicialização, health check, bootstrap idempotente e persistência após recriação do serviço.

## Blocked by

None - can start immediately
