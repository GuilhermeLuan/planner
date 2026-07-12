# Tela do Dia e ciclo completo de Tarefas

Status: complete

## What to build

Entregar a experiência diária principal do Planner para Tarefas. A pessoa deve entrar em Hoje, navegar por Dias próximos ou pelo calendário mensal, visualizar Tarefas em seções previsíveis e executar todo o ciclo de uma Tarefa: criar, editar, concluir, desfazer conclusão, reagendar para outro Dia, arquivar e restaurar.

As interações devem continuar funcionando offline e aproveitar o caminho de sincronização do slice anterior. A tela deve separar Rotinas de Tarefas, ordenar itens com horário e manter itens sem horário em uma seção própria.

## Acceptance criteria

- [x] A abertura do app leva ao Dia atual e permite navegar pela faixa de datas e pelo calendário mensal, com semana iniciando na segunda-feira.
- [x] A tela separa Rotinas e Tarefas e ordena itens com horário, mantendo itens sem horário em seção própria.
- [x] A pessoa consegue criar e editar Tarefas com ou sem horário para um Dia específico.
- [x] Concluir e desfazer a conclusão de uma Tarefa atualiza o estado local imediatamente e sincroniza a mudança.
- [x] Reagendar uma Tarefa move a mesma entidade para outro Dia, sem duplicação.
- [x] Arquivar e restaurar uma Tarefa são reversíveis e preservam o estado sincronizado.
- [x] A experiência completa permanece utilizável sem internet e sinaliza operações pendentes ou falhas de sincronização.
- [x] Testes instrumentados de Compose e testes de persistência cobrem Hoje, navegação, ordenação, CRUD, conclusão, reagendamento, arquivamento e restauração.

## Blocked by

- 04-sync-tarefa-tracer-bullet.md
