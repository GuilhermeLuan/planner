# Rotinas e Ocorrências de rotina

Status: ready-for-agent

## What to build

Entregar o ciclo completo de Rotinas recorrentes integrado à tela do Dia e ao sync incremental existente. A pessoa deve definir uma Rotina por dias da semana, data de início e horário opcional; o Planner deve projetar Ocorrências de rotina nos Dias correspondentes e manter o estado de cada ocorrência independente.

Editar, pausar/arquivar e restaurar a Rotina não pode apagar o histórico já registrado. Concluir, pular ou devolver uma ocorrência ao estado pendente deve atravessar armazenamento local, API e os demais clientes da Conta.

## Acceptance criteria

- [ ] A pessoa consegue criar e editar uma Rotina com título, dias da semana, data de início e horário opcional.
- [ ] Ocorrências aparecem automaticamente somente nos Dias escolhidos e a partir da data de início.
- [ ] Cada Ocorrência de rotina mantém estado independente entre Dias, com transições pendente, concluída, pulada e de volta a pendente.
- [ ] Arquivar ou pausar uma Rotina impede novas aparições futuras sem apagar ocorrências ou histórico existentes.
- [ ] Restaurar uma Rotina arquivada volta a gerar ocorrências futuras conforme sua configuração.
- [ ] Alterações de Rotina e ocorrência funcionam offline, entram na outbox e são observáveis em outro cliente após push/pull.
- [ ] Testes de domínio, contrato e Compose cobrem recorrência, início, ordenação, estados independentes, arquivamento, restauração e sincronização.

## Blocked by

- 05-dia-e-tarefas.md
