# Fuso da Conta, notificações locais e acessibilidade

Status: complete

## What to build

Entregar as configurações de tempo e os comportamentos locais que dependem delas. O Android deve sugerir o fuso detectado, permitir ajustar o Fuso da Conta e usar essa referência para interpretar Dias, horários, recorrências e notificações. Tarefas e Rotinas com horário devem gerar notificações locais reconstruíveis a partir do estado local.

Completar também o acabamento transversal da interface Android com tema rosa único, modo claro/escuro seguindo o sistema, contraste suficiente e estados de foco e erro legíveis.

## Acceptance criteria

- [x] O primeiro bootstrap sugere o fuso do dispositivo e a Conta consegue alterá-lo por uma configuração persistida e sincronizada.
- [x] Dias, horários, recorrências e notificações usam o Fuso da Conta, inclusive em viradas de data relevantes.
- [x] Tarefas e Rotinas com horário podem ser configuradas para gerar notificações locais no Android.
- [x] Notificações são canceladas ou atualizadas quando o item muda, é concluído ou arquivado, e são reconstruídas após reinicialização ou sincronização.
- [x] O tema rosa funciona em modo claro e escuro seguindo o sistema, mantendo contraste, foco, erro e leitura acessíveis.
- [x] Testes cobrem fuso inicial e alterado, fronteiras de data, agendamento, reconstrução, cancelamento e estados de acessibilidade observáveis na UI.

## Blocked by

- 03-configuracao-android-sessao.md
- 05-dia-e-tarefas.md
- 06-rotinas-ocorrencias.md
