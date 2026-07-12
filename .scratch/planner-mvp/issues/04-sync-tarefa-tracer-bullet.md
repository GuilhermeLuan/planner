# Sync incremental tracer bullet com Tarefa

Status: complete

## What to build

Entregar o primeiro caminho completo de sincronização usando uma Tarefa como entidade tracer bullet. Uma alteração feita no armazenamento Room local deve gerar uma operação de outbox, ser enviada pelo WorkManager à API REST/JSON, ser aplicada no estado canônico da Conta e poder ser baixada por outro cliente usando cursor incremental. O fluxo deve funcionar com reenvio seguro, isolamento por Conta e conectividade intermitente.

Este slice estabelece o contrato reutilizável de push/pull, IDs idempotentes, versões, paginação e indicação observável de sincronização para as entidades seguintes.

## Acceptance criteria

- [x] Criar ou alterar uma Tarefa offline atualiza imediatamente a leitura local e cria uma operação pendente com identificador idempotente.
- [x] O WorkManager envia operações quando houver rede, aplica retry com backoff e só remove uma operação após confirmação do backend.
- [x] O backend aceita um lote de operações, persiste a mudança por Conta e retorna o resultado de cada identificador sem duplicar reenvios.
- [x] Pull com cursor retorna somente mudanças posteriores, informa o próximo cursor e suporta lotes menores que o conjunto total.
- [x] Dois clientes da mesma Conta conseguem observar a mudança após push/pull; um cliente de outra Conta nunca recebe a entidade.
- [x] Alterações concorrentes sobre a mesma Tarefa seguem a política de última versão aceita pelo servidor e deixam o estado final observável nos clientes.
- [x] A UI expõe estado de sincronização e falha recuperável sem bloquear a operação offline.
- [x] Testes de contrato e de integração com dois clientes lógicos cobrem push, pull, cursor, retry idempotente, isolamento, concorrência e recuperação após ausência de rede.

## Blocked by

- 03-configuracao-android-sessao.md
