# Onboarding local e abertura do Planner

## Objetivo

Em uma instalação nova, pedir somente o nome da pessoa, sugerir o fuso do dispositivo e criar uma Conta e seu Planner no Room. Nas aberturas seguintes, restaurar diretamente o mesmo Planner e abrir o Dia atual sem depender de rede, servidor, credenciais ou sessão remota.

## Arquitetura

O Room é a fonte canônica. `LocalPlannerRepository` oferece uma interface pequena para criar e restaurar a identidade local. A criação normaliza e valida o nome, valida o identificador de fuso, gera UUIDs e persiste Conta, Planner e identidade ativa em uma única transação.

O estado raiz possui somente `Loading`, `NeedsOnboarding` e `Ready`. O primeiro consulta Room; o segundo exibe nome e fuso sugerido; o último vincula o Planner ao Dia. Não há configuração de servidor, login, logout, token, bloqueio remoto ou agendamento de sincronização no caminho do aplicativo.

As tabelas remotas antigas podem permanecer temporariamente para uma migração conservadora, mas operações de Tarefa e Rotina não criam novas entradas na outbox e o worker de sincronização não executa trabalho remoto.

## Fluxo e falhas

Nome vazio após `trim` é rejeitado com mensagem legível. A confirmação é bloqueada durante a gravação. Uma falha local mantém o onboarding aberto. Após sucesso, o estado muda diretamente para `Ready`; em reabertura, os IDs persistidos são restaurados sem criar outra identidade.

O Dia atual é calculado pelo fuso persistido da Conta. Notificações continuam locais.

## Testes

Os testes cobrem criação e restauração estáveis, normalização e rejeição do nome vazio, persistência do fuso, contrato visual sem campos remotos e compilação do aplicativo e dos testes instrumentados. A validação final executa toda a suíte unitária e, quando houver dispositivo/emulador, a suíte instrumentada.
