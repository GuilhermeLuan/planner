# Desativação e recuperação da Conta

Status: complete

## What to build

Completar o comportamento de segurança quando uma Conta é desativada enquanto um aparelho está offline. O backend deve impedir novas sessões e sincronizações para a Conta desativada; o cliente já autenticado pode continuar localmente até o próximo contato, mas deve bloquear a sessão e tornar os dados locais inacessíveis assim que receber a revogação.

Após reativação pela Conta administradora, a pessoa deve conseguir autenticar novamente e recuperar o acesso ao estado local preservado, sem misturar dados ou criar um Planner paralelo.

## Acceptance criteria

- [x] Uma Conta desativada não consegue iniciar login nem executar push/pull no backend.
- [x] Um aparelho autenticado continua operando localmente enquanto permanece sem contato com o servidor.
- [x] No próximo sync, a revogação é reconhecida, a sessão é bloqueada e os dados locais ficam inacessíveis para a Conta desativada.
- [x] A UI informa o motivo do bloqueio e orienta a reativação ou novo login, sem apagar silenciosamente o estado local.
- [x] Após reativação no painel e login válido, a pessoa recupera o acesso ao Planner preservado e pode sincronizar novamente.
- [x] Testes de integração cobrem a sequência offline, desativação, próximo sync, bloqueio, reativação e recuperação.

## Blocked by

- 02-painel-ciclo-contas.md
- 03-configuracao-android-sessao.md
- 04-sync-tarefa-tracer-bullet.md
