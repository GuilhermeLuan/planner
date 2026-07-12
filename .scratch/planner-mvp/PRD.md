# PRD — Planner pessoal self-hosted offline-first

Status: ready-for-agent

## Problem Statement

Pessoas que desejam organizar o próprio dia precisam acompanhar Rotinas recorrentes e Tarefas pontuais mesmo quando estão sem internet, sem entregar seus dados a um serviço de terceiros e sem depender da disponibilidade contínua de um servidor. As soluções existentes costumam assumir conexão permanente, misturar recorrências com tarefas comuns ou exigir serviços externos para autenticação, notificações e operação.

O Planner precisa oferecer uma experiência diária simples no Android, com dados privados por Conta, operação offline confiável e sincronização posterior entre dispositivos. Ao mesmo tempo, quem opera a instalação precisa conseguir hospedá-la com pouca infraestrutura, administrar Contas sem acessar seus Planners e manter backups recuperáveis.

## Solution

Construir um Planner pessoal self-hosted cujo cliente principal é um app Android nativo. A pessoa começa na tela do Dia, visualiza Rotinas e Tarefas separadamente, registra o estado de cada ocorrência, cria e altera itens sem internet e recebe notificações locais para itens com horário.

O app usa um Planner local como fonte imediata de leitura e escrita. Alterações são enfileiradas e sincronizadas de forma incremental e idempotente com um backend canônico por Conta quando houver rede. A instalação atende várias Contas sem misturar dados, oferece autenticação local e inclui um painel web restrito à Conta administradora para provisionamento e recuperação de acesso.

O backend será distribuído como um serviço self-hosted leve, com persistência SQLite, API REST/JSON versionada, painel administrativo server-rendered, Docker Compose e procedimento configurável de backup. O MVP privilegia funcionamento previsível, baixa complexidade operacional e um primeiro vertical slice utilizável de ponta a ponta.

## User Stories

1. Como pessoa que planeja o dia, quero abrir o app diretamente em Hoje, para que eu veja rapidamente o que pretendo acompanhar ou realizar.
2. Como pessoa que planeja o dia, quero navegar por uma faixa de datas, para que eu consulte Dias próximos sem perder o contexto atual.
3. Como pessoa que planeja o dia, quero abrir um calendário mensal como navegação secundária, para que eu alcance datas mais distantes com poucos passos.
4. Como pessoa que planeja o dia, quero que a semana comece na segunda-feira, para que a navegação siga a convenção definida pelo Planner.
5. Como pessoa que planeja o dia, quero ver Rotinas e Tarefas em seções separadas, para que eu diferencie hábitos recorrentes de atividades pontuais.
6. Como pessoa que planeja o dia, quero ver itens com horário ordenados cronologicamente, para que eu entenda a sequência planejada do Dia.
7. Como pessoa que planeja o dia, quero ver itens sem horário em uma seção própria, para que atividades de dia inteiro não pareçam ter um horário implícito.
8. Como pessoa que mantém hábitos, quero criar uma Rotina com título e dias da semana, para que ela apareça automaticamente nos Dias escolhidos.
9. Como pessoa que mantém hábitos, quero definir uma data de início para a Rotina, para que ocorrências não sejam criadas antes de ela começar.
10. Como pessoa que mantém hábitos, quero atribuir opcionalmente um horário à Rotina, para que ela participe da ordenação e possa gerar uma notificação local.
11. Como pessoa que mantém hábitos, quero editar uma Rotina, para que mudanças futuras no meu planejamento sejam refletidas.
12. Como pessoa que mantém hábitos, quero pausar ou arquivar uma Rotina, para que ela deixe de aparecer sem que seu histórico seja apagado.
13. Como pessoa que mantém hábitos, quero restaurar uma Rotina arquivada, para que eu possa retomá-la sem recriá-la.
14. Como pessoa que acompanha uma Rotina, quero concluir sua ocorrência em um Dia, para que o resultado daquele Dia seja registrado sem alterar os demais.
15. Como pessoa que acompanha uma Rotina, quero pular sua ocorrência em um Dia, para que eu diferencie uma decisão consciente de uma pendência.
16. Como pessoa que acompanha uma Rotina, quero devolver uma ocorrência ao estado pendente, para que eu corrija uma marcação acidental.
17. Como pessoa que acompanha uma Rotina, quero que cada ocorrência tenha estado independente, para que concluir ou pular hoje não modifique outros Dias.
18. Como pessoa que organiza atividades pontuais, quero criar uma Tarefa para um Dia específico, para que ela apareça somente na data planejada.
19. Como pessoa que organiza atividades pontuais, quero criar uma Tarefa sem horário, para que eu represente algo que pode ser feito a qualquer momento do Dia.
20. Como pessoa que organiza atividades pontuais, quero criar uma Tarefa com horário, para que ela seja ordenada e possa gerar uma notificação local.
21. Como pessoa que organiza atividades pontuais, quero editar uma Tarefa, para que eu atualize seu título ou horário.
22. Como pessoa que organiza atividades pontuais, quero concluir uma Tarefa, para que eu acompanhe o que já realizei.
23. Como pessoa que organiza atividades pontuais, quero devolver uma Tarefa concluída ao estado pendente, para que eu corrija uma marcação acidental.
24. Como pessoa que organiza atividades pontuais, quero reagendar manualmente uma Tarefa para outro Dia, para que uma mudança de plano não crie uma atividade duplicada.
25. Como pessoa que organiza atividades pontuais, quero arquivar uma Tarefa, para que ela saia do fluxo diário sem ser removida permanentemente.
26. Como pessoa que organiza atividades pontuais, quero restaurar uma Tarefa arquivada, para que eu possa recuperá-la após um arquivamento acidental.
27. Como pessoa com conectividade instável, quero consultar e alterar meu Planner sem internet, para que a organização diária não dependa da rede.
28. Como pessoa com conectividade instável, quero que alterações offline sejam enfileiradas automaticamente, para que eu não precise lembrar de sincronizá-las individualmente.
29. Como pessoa com conectividade instável, quero que a sincronização seja tentada ao abrir ou retomar o app, para que meus dados se atualizem naturalmente.
30. Como pessoa com conectividade instável, quero que a sincronização seja tentada após alterações quando houver rede, para que outros dispositivos recebam as mudanças rapidamente.
31. Como pessoa com conectividade instável, quero iniciar uma sincronização manual, para que eu tenha controle quando precisar confirmar a atualização.
32. Como pessoa com conectividade instável, quero ver o estado da última sincronização e falhas acionáveis, para que eu saiba se os dados chegaram ao servidor.
33. Como pessoa que usa mais de um dispositivo, quero que o reenvio de uma mesma operação seja seguro, para que tentativas automáticas não dupliquem mudanças.
34. Como pessoa que usa mais de um dispositivo, quero receber apenas mudanças posteriores ao último cursor conhecido, para que a sincronização seja eficiente.
35. Como pessoa que usa mais de um dispositivo, quero que a última versão aceita pelo servidor prevaleça em alterações concorrentes, para que conflitos sejam resolvidos de forma previsível no MVP.
36. Como titular de uma Conta, quero que somente dados do meu Planner sejam retornados pela API, para que outra Conta da instalação não acesse minhas informações.
37. Como titular de uma Conta, quero informar a URL do meu servidor na primeira configuração, para que eu possa usar minha própria instalação self-hosted.
38. Como titular de uma Conta, quero usar servidores HTTP locais ou HTTPS, para que o app funcione em diferentes ambientes self-hosted.
39. Como titular de uma Conta, quero receber um aviso claro antes de usar HTTP, para que eu entenda que credenciais e dados não estarão protegidos em trânsito.
40. Como titular de uma Conta, quero que o primeiro login valide minhas credenciais e baixe meu Planner, para que o estado offline inicial seja confiável.
41. Como titular de uma Conta, quero abrir o Planner offline após o primeiro login, para que uma indisponibilidade do servidor não impeça o uso diário.
42. Como titular de uma Conta, quero que tokens e segredos de sessão sejam protegidos pelo Android Keystore, para que não fiquem armazenados em texto puro.
43. Como titular de uma Conta, quero manter somente uma Conta ativa por dispositivo, para que Planners não sejam misturados acidentalmente.
44. Como titular de uma Conta, quero sair explicitamente e entrar com outra Conta, para que a troca de identidade seja consciente e segura.
45. Como titular de uma Conta recém-criada, quero trocar a senha temporária no primeiro login, para que somente eu conheça minha senha definitiva.
46. Como titular de uma Conta, quero ajustar meu Fuso da Conta, para que Dias, horários, recorrências e notificações correspondam à minha localização.
47. Como titular de uma Conta, quero que o Android sugira inicialmente seu fuso detectado, para que a configuração correta exija pouco esforço.
48. Como titular de uma Conta, quero receber notificações locais configuráveis para Tarefas com horário, para que eu seja lembrado mesmo sem conexão.
49. Como titular de uma Conta, quero receber notificações locais configuráveis para Rotinas com horário, para que eu seja lembrado mesmo sem conexão.
50. Como titular de uma Conta, quero que notificações sejam reconstruídas a partir do estado local, para que continuem corretas após reinicializações ou sincronizações.
51. Como titular de uma Conta, quero usar modo claro ou escuro seguindo o sistema, para que o app respeite minha preferência do dispositivo.
52. Como titular de uma Conta, quero uma interface mobile-first com contraste, foco e erros legíveis, para que a paleta rosa não prejudique a acessibilidade.
53. Como Conta administradora, quero usar meu próprio Planner normalmente, para que a permissão administrativa não me transforme em um tipo separado de pessoa.
54. Como Conta administradora, quero entrar em um painel web protegido, para que operações administrativas não dependam do app Android.
55. Como Conta administradora, quero criar uma Conta com nome de usuário, senha temporária e fuso, para que outra pessoa possa começar a usar a instalação.
56. Como Conta administradora, quero impedir autocadastro público, para que somente pessoas autorizadas recebam uma Conta.
57. Como Conta administradora, quero consultar as Contas da instalação e seus estados, para que eu administre o acesso.
58. Como Conta administradora, quero consultar o último acesso e a última sincronização de uma Conta, para que eu diagnostique problemas sem abrir seu Planner.
59. Como Conta administradora, quero desativar uma Conta, para que novos logins e sincronizações sejam bloqueados.
60. Como Conta administradora, quero reativar uma Conta, para que a pessoa recupere o acesso sem perder seu Planner.
61. Como Conta administradora, quero redefinir a senha de uma Conta para uma senha temporária, para que a pessoa recupere o acesso sem serviço de e-mail.
62. Como Conta administradora, quero encerrar minha sessão administrativa, para que o painel não permaneça acessível no dispositivo usado.
63. Como titular de uma Conta desativada, quero que o app bloqueie a sessão no próximo contato com o servidor, para que a política de acesso seja aplicada mesmo após um período offline.
64. Como titular de uma Conta desativada, quero que os dados locais permaneçam inacessíveis até reativação e login válido, para que o bloqueio não exponha meu Planner.
65. Como titular de uma Conta reativada, quero voltar a acessar o estado local após autenticação válida, para que a desativação não apague meu planejamento.
66. Como Conta administradora, quero que o painel não permita visualizar nem editar Planners, para que a administração de acesso preserve a privacidade das demais Contas.
67. Como operador da instalação, quero inicializar a primeira Conta administradora por configuração segura do serviço, para que uma instalação vazia possa ser provisionada.
68. Como operador da instalação, quero verificar a saúde do serviço por um endpoint simples, para que eu possa monitorar sua disponibilidade.
69. Como operador da instalação, quero subir o backend com Docker Compose, para que instalação e atualização tenham poucos passos.
70. Como operador da instalação, quero manter o SQLite em volume persistente, para que recriar o container não apague os dados.
71. Como operador da instalação, quero escolher a pasta de backup e a retenção, para que a rotina se adapte ao armazenamento disponível.
72. Como operador da instalação, quero produzir backups consistentes do SQLite, para que eu consiga recuperar a instalação após uma falha.
73. Como operador da instalação, quero instruções de restauração validadas, para que um arquivo de backup seja realmente útil durante um incidente.
74. Como operador da instalação, quero uma recomendação explícita de HTTPS para acesso em rede, para que eu configure a exposição do serviço com consciência dos riscos.
75. Como desenvolvedor de um cliente futuro, quero uma API versionada e independente do Android, para que novas interfaces possam reutilizar o domínio sem acoplamento ao primeiro cliente.

## Implementation Decisions

- O produto será entregue como um primeiro vertical slice ponta a ponta, composto pelo cliente Android, backend self-hosted, painel administrativo e pacote de deploy.
- O cliente principal será Android nativo em Kotlin com Jetpack Compose. O domínio e o contrato de sincronização não dependerão de componentes específicos da interface Android.
- O app será offline-first. A UI sempre lerá e escreverá no banco Room local; chamadas remotas não ficarão no caminho crítico das interações diárias.
- O modelo local representará Conta, Planner, Rotina, Ocorrência de rotina, Tarefa, operações pendentes e metadados de sincronização. Os dados serão particionados por Conta mesmo havendo somente uma Conta ativa por dispositivo.
- Uma Rotina terá título, dias da semana, data de início, horário opcional e estado de arquivamento. Sua recorrência no MVP será somente por dias da semana.
- Uma Ocorrência de rotina pertencerá a uma Rotina e a um Dia e terá estado independente entre pendente, concluída e pulada.
- Uma Tarefa pertencerá a um Dia, terá título, horário opcional, estado pendente ou concluído e estado de arquivamento. Mover uma Tarefa será um reagendamento explícito.
- O arquivamento de Rotinas e Tarefas será reversível. O estado arquivado será preservado e sincronizado como tombstone; remoção física não fará parte do fluxo normal.
- O Fuso da Conta será a referência para Dias, horários, recorrências e notificações. O Android sugerirá o fuso inicial, e a Conta poderá ajustá-lo.
- A semana começará na segunda-feira em toda navegação e cálculo de recorrência.
- A tela inicial do Android será o Dia atual. A navegação primária usará uma faixa de datas, e o calendário mensal será secundário.
- Rotinas e Tarefas aparecerão separadas. Em cada seção, itens com horário serão ordenados cronologicamente e itens sem horário serão agrupados separadamente.
- O tema do MVP será rosa, com modos claro e escuro seguindo o sistema e com estados de foco, erro e contraste acessíveis.
- O primeiro acesso exigirá URL explícita do servidor e conexão válida. HTTP e HTTPS serão aceitos, com alerta de risco antes do uso de HTTP.
- O primeiro login baixará a Conta e o Planner para inicializar o armazenamento local. Depois desse bootstrap, a sessão poderá abrir e operar offline.
- Tokens e segredos de sessão ficarão no Android Keystore. O Room dependerá da criptografia padrão do dispositivo; SQLCipher não será incorporado no MVP.
- Um dispositivo manterá uma única Conta ativa. Trocar de Conta exigirá logout e novo login.
- Cada mutação local produzirá uma operação de outbox com identificador idempotente. Uma operação só sairá da fila após confirmação inequívoca do backend.
- A sincronização será disparada ao abrir ou retomar o app, após alterações quando houver rede e por ação manual. O WorkManager aplicará restrição de rede, retries e backoff; não haverá serviço permanente.
- O protocolo será REST/JSON incremental e versionado desde o início. O push aceitará lotes de operações e informará resultados por identificador; o pull retornará mudanças posteriores a um cursor e o próximo cursor.
- O contrato de push distinguirá operações confirmadas, rejeitadas e conflitantes para que o cliente não descarte trabalho silenciosamente. Reenvios do mesmo identificador serão idempotentes.
- O backend manterá a visão canônica isolada por Conta. Em concorrência entre dispositivos, prevalecerá a versão aceita mais recentemente pelo servidor, sem interface de resolução manual no MVP.
- Mudanças sincronizadas terão versão e registro incremental suficientes para reconstruir o estado de cada cliente sem baixar o Planner inteiro em todas as execuções.
- O backend será um serviço Go com Chi e SQLite. Migrações prepararão o banco automaticamente e manterão integridade referencial e isolamento por Conta.
- A API inicial oferecerá autenticação, troca de senha, logout, consulta da Conta atual, push, pull e health check sob contrato versionado.
- A autenticação será local por nome de usuário e senha. Senhas serão persistidas somente como hashes; sessões poderão expirar e ser revogadas.
- A primeira Conta administradora será criada no bootstrap da instalação. Ela continuará sendo uma Conta comum com Planner e receberá uma permissão administrativa adicional.
- Não haverá autocadastro. A Conta administradora criará Contas com senha temporária, e a troca dessa senha será obrigatória no primeiro login.
- A redefinição de senha será feita somente pela Conta administradora e voltará a exigir troca no próximo login. Não haverá SMTP, convite ou recuperação por e-mail.
- A desativação bloqueará novos logins e sincronizações. Um dispositivo offline poderá continuar operando até o próximo contato; ao receber a revogação, bloqueará a sessão e tornará o estado local inacessível até reativação e autenticação válida.
- O painel administrativo será HTML server-rendered pelo backend, responsivo e com JavaScript mínimo. Somente a Conta administradora poderá acessá-lo.
- O painel permitirá criar, ativar e desativar Contas, redefinir senhas, consultar último acesso e última sincronização e encerrar a sessão. Ele não terá acesso ao conteúdo dos Planners.
- Notificações serão locais no Android e poderão ser configuradas para Tarefas e Rotinas com horário. O agendamento será reconstituído do estado local após mudanças relevantes; não haverá push do backend.
- O deploy oficial usará Docker Compose, com SQLite em volume persistente fora do ciclo de vida do container.
- O procedimento de backup usará pasta e retenção configuráveis no host. A documentação incluirá criação, verificação e restauração para que recuperabilidade seja testável.
- A fundação de backend já iniciada será tratada como ponto de partida, não como contrato final: autenticação, autorização administrativa, resultados do push, último sync, validação de payloads e comportamento de desativação deverão ser alinhados integralmente a este PRD.

## Testing Decisions

- Bons testes observarão comportamento externo e resultados persistidos, evitando afirmar detalhes internos como nomes de funções, consultas SQL específicas, composição de ViewModels ou estrutura privada da outbox.
- O seam principal do backend será o roteador HTTP completo conectado a um SQLite temporário real. Os testes enviarão requisições REST ou formulários e validarão status, contratos, autorização e estado observável subsequente.
- Os fluxos de autenticação cobrirão bootstrap, login válido e inválido, troca obrigatória de senha temporária, expiração ou revogação, logout, isolamento por Conta e bloqueio de Conta desativada.
- O painel administrativo será testado no nível HTTP/HTML, cobrindo autorização exclusiva da Conta administradora, criação, duplicidade, ativação, desativação, redefinição de senha, metadados exibidos e logout, sem acoplar os testes aos templates internos.
- O seam principal da sincronização será um cenário com dois clientes lógicos e um backend real: cada cliente manterá cursor e operações próprios, permitindo verificar push, pull, retry idempotente, isolamento por Conta, ordenação, paginação, arquivamento reversível e última versão aceita.
- Testes de contrato verificarão que cada operação enviada recebe resultado explícito e que operações inválidas, rejeitadas ou conflitantes não desaparecem silenciosamente da fila do cliente.
- Testes de conflito produzirão alterações concorrentes sobre a mesma entidade em clientes distintos e validarão, pelo estado final baixado, a política de última versão aceita pelo servidor.
- O seam principal do domínio Android será o repositório local sobre Room em memória, exercitado pelos casos de uso públicos. Os testes validarão a projeção do Dia, recorrências, independência das ocorrências, ordenação, reagendamento, arquivamento e particionamento por Conta.
- Os fluxos críticos de interface Android terão testes instrumentados de Compose: primeira configuração, alerta HTTP, login e troca de senha, Hoje, navegação de Dia, criação e alteração de Rotina e Tarefa, conclusão, pulo, reagendamento, arquivamento, restauração e indicação de sincronização.
- A integração Android de sincronização será testada com um servidor controlável no nível HTTP e WorkManager testável, cobrindo uso offline, criação da outbox, retomada com rede, backoff, confirmação parcial e preservação de operações não confirmadas.
- O bloqueio após desativação será testado como fluxo: uso offline permitido antes do contato, resposta de revogação no sync, sessão bloqueada, dados inacessíveis e recuperação apenas após reativação e login válido.
- Notificações serão testadas no seam do agendador Android, validando os alarmes observáveis derivados de itens com horário, alterações, conclusão, arquivamento, troca de fuso e reconstrução após reinicialização ou sincronização.
- Cálculos de Dia e recorrência terão casos em fusos distintos, transições de horário de verão quando aplicáveis, virada de data e semana iniciada na segunda-feira.
- O deploy terá um smoke test que sobe a composição, aguarda o health check, autentica uma Conta e comprova persistência após recriar o container.
- O backup terá teste de recuperação: criar dados conhecidos, executar o procedimento, restaurar o arquivo em uma instalação limpa e validar os dados por interfaces públicas do servidor.
- Como o repositório ainda não possui uma suíte estabelecida, o prior art inicial será o comportamento já exposto pelo roteador HTTP e pelo armazenamento SQLite. Novos testes deverão consolidar esses seams de alto nível antes de testes unitários mais estreitos.

## Out of Scope

- Um Planner web completo ou qualquer edição de Planner pelo painel administrativo.
- Cliente iOS.
- Colaboração, compartilhamento ou edição conjunta entre Contas.
- Atualização em tempo real, WebSocket, serviço Android permanente ou sincronização instantânea garantida.
- Resolução manual de conflitos ou histórico visual de versões.
- Autocadastro público, SMTP, convites por e-mail e recuperação automática de senha.
- Login social, Google, Apple, OIDC ou outros provedores externos de identidade.
- Temas customizáveis além do tema rosa e dos modos claro e escuro do sistema.
- Anexos, PDFs, journal, notas longas ou armazenamento de arquivos.
- Recorrências avançadas, como “a cada N dias”, exceções complexas ou regras de calendário além de dias da semana.
- Relatórios, analytics ou visualização administrativa do conteúdo dos Planners.
- Remoção física rotineira de Rotinas e Tarefas arquivadas.
- Criptografia SQLCipher do banco Room no MVP.
- Notificações push enviadas pelo backend.
- Descoberta automática de servidor na rede.
- Backup dos bancos locais dos dispositivos Android pelo backend.

## Further Notes

- Este PRD sintetiza o contexto de domínio, o desenho de arquitetura e as decisões registradas na sessão de grill. Em caso de ambiguidade de implementação, os termos Conta, Conta administradora, Fuso da Conta, Planner, Dia, Rotina, Ocorrência de rotina e Tarefa devem conservar os significados do glossário do projeto.
- O repositório já contém uma fundação parcial do servidor e do deploy. O app Android, a validação completa dos contratos e a suíte de testes ainda precisam ser entregues; a existência de scaffolding não reduz os comportamentos exigidos por este PRD.
- O primeiro corte deve permanecer vertical: uma Conta provisionada precisa conseguir configurar o Android, autenticar, planejar um Dia offline, sincronizar, observar a mudança em outro cliente e recuperar o serviço a partir de backup antes de o MVP ser considerado concluído.
- Mudanças que contrariem as decisões offline-first, autenticação local, cliente Kotlin, Room, WorkManager, Go/Chi/SQLite, sync incremental, arquivamento reversível, painel administrativo limitado ou deploy Compose exigem revisão explícita dos ADRs correspondentes.
