# Backup, restauração e smoke test operacional

Status: ready-for-agent

## What to build

Entregar a rotina operacional de backup do SQLite e um caminho documentado e testado de restauração. O operador deve escolher pasta e retenção no host, gerar cópias consistentes sem depender do ciclo de vida do container e validar que uma instalação limpa consegue recuperar Contas, Planners e estado sincronizado a partir de um backup.

## Acceptance criteria

- [ ] A pasta de destino e a retenção de backups são configuráveis sem alterar o código da aplicação.
- [ ] A rotina gera um arquivo nomeado e consistente do SQLite, com limpeza de arquivos além da retenção configurada.
- [ ] A documentação explica pré-condições, execução, verificação e restauração do backup.
- [ ] Um backup restaurado em uma instalação limpa recupera Contas, Planners, itens planejados e dados necessários para continuar sincronizando.
- [ ] O procedimento não grava backups nos dispositivos Android nem depende do container permanecer vivo após a cópia.
- [ ] Testes operacionais sobem a composição, criam dados conhecidos, executam backup, restauram e validam os dados pelas interfaces públicas.

## Blocked by

- 01-bootstrap-servico-persistencia.md
- 02-painel-ciclo-contas.md
- 03-configuracao-android-sessao.md
- 04-sync-tarefa-tracer-bullet.md
- 05-dia-e-tarefas.md
- 06-rotinas-ocorrencias.md
- 07-fuso-notificacoes-acessibilidade.md
- 08-desativacao-recuperacao-conta.md
