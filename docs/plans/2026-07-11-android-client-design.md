# Cliente Android offline-first — design de implementação

> **Substituído pela ADR 0023.** As partes de login, servidor, Keystore e sincronização são apenas registro histórico.

Status: aprovado para implementação em 2026-07-11.

## Recorte

Implementar as issues Android `03` a `08` em ordem, preservando Kotlin nativo, Jetpack Compose, Room, WorkManager, sessão protegida pelo Android Keystore e sincronização REST incremental.

O projeto começa em um único módulo `:app`, organizado por funcionalidades. Essa escolha mantém o primeiro slice navegável e verificável; as fronteiras públicas permitem extrair módulos Gradle depois sem alterar o comportamento.

## Interfaces públicas

- `ServerConfiguration`: valida e normaliza URLs HTTP/HTTPS e informa se a conexão exige confirmação de risco.
- `SessionRepository`: configura servidor, autentica, troca senha, restaura sessão local, bloqueia sessão revogada e faz logout.
- `PlannerRepository`: observa um Dia e executa comandos sobre Tarefas, Rotinas e Ocorrências; toda mutação local é atômica com a outbox.
- `SyncCoordinator`: agenda e executa sincronização, expondo `SyncStatus` observável.
- `NotificationScheduler`: reconstrói, agenda e cancela notificações a partir do estado local.

HTTP, relógio, geração de IDs, armazenamento de segredo e agendamento Android entram por interfaces de fronteira. Room continua sendo a fonte de leitura e escrita da UI.

## Fluxos verticais TDD

1. Configurar URL segura; depois adicionar confirmação HTTP e validação de erros.
2. Login conectado grava Conta/Planner e segredo protegido; depois troca obrigatória de senha, reabertura offline, logout e troca de Conta.
3. Criar Tarefa offline e observar imediatamente no Dia; depois outbox, push, pull, retry, isolamento e concorrência.
4. Completar navegação e ciclo de Tarefas.
5. Projetar Rotinas e estados independentes das Ocorrências.
6. Aplicar Fuso da Conta, notificações reconstruíveis e acabamento acessível.
7. Bloquear sessão no próximo contato após desativação e recuperar o mesmo Planner após reativação.

Cada item segue um ciclo independente: um teste de comportamento pela interface pública falha, a implementação mínima o torna verde e só então ocorre refatoração.

## Persistência

Room particiona todas as entidades por `accountId`. Tarefa, Rotina, Ocorrência, metadados de sincronização e operação de outbox vivem no mesmo banco. Comandos que alteram uma entidade e criam operação usam uma única transação.

O token não entra no Room nem em preferências comuns. Uma chave AES do Android Keystore protege o segredo persistido em arquivo privado. Metadados não secretos de bootstrap podem usar armazenamento privado comum.

## Sincronização e erros

O cliente envia operações pendentes em lotes. Somente `accepted` ou `duplicate` removem a operação. `rejected` permanece observável como falha acionável. Pull aplica versões do servidor e avança o cursor somente após a transação local concluir.

Ausência de rede mantém o app operacional. `401` invalida a sessão; `account_disabled` bloqueia acesso local sem apagar dados; falhas transitórias retornam retry com backoff.

## UI

O sistema visual está em `.interface-design/system.md`. A tela do Dia usa a Fita do Dia como navegação primária, uma folha contínua para Rotinas e Tarefas e rosa framboesa somente para ações/seleções. Claro e escuro seguem o sistema.

Compose expõe semântica para nomes, estados, erros e ações. Alvos têm ao menos 48dp, textos suportam escala e o layout é validado em larguras compactas.

## Verificação

- Testes JVM para valores, domínio e casos de uso puros.
- Testes Room instrumentados para persistência, partição e transações de outbox.
- Testes de contrato HTTP em servidor controlado no limite externo.
- Testes WorkManager para restrição de rede, retry e confirmação.
- Testes Compose instrumentados para fluxos e semântica observável.
- Build `assembleDebug`, testes JVM e testes instrumentados quando houver dispositivo/emulador disponível.
