# Exportação de logs de diagnóstico

## Objetivo

Permitir que uma pessoa exporte evidências técnicas do Planner em um arquivo ZIP quando um problema acontece em um dispositivo sem acesso a ADB. O primeiro caso de uso é diagnosticar lembretes que não aparecem ou não produzem som, mantendo o aplicativo local-only e protegendo os dados da Conta.

## Princípios

- Logs são estruturados, locais, limitados e exportados somente por ação explícita.
- Nenhum evento contém nome da pessoa, título de Tarefa ou Rotina, conteúdo do Planner, backup ou identificador real.
- Falhas do logger nunca interrompem uma operação do Planner.
- O pacote diferencia fatos observados de estados que o Android não permite consultar.
- Nenhum SDK remoto ou permissão ampla de armazenamento será adicionado.

## Arquitetura

### DiagnosticLogger

Recebe eventos estruturados e os grava em JSON Lines no armazenamento interno privado do aplicativo. Cada linha é independente e contém:

- timestamp UTC;
- nome estável do evento;
- correlation ID anonimizado;
- versão do formato;
- detalhes técnicos permitidos para aquele evento.

O correlation ID será derivado do identificador interno usando hash com um segredo local. Ele permite seguir o mesmo lembrete entre agendamento, receiver e publicação sem revelar o ID original.

O logger manterá no máximo sete dias de eventos e 1 MB. A retenção remove primeiro os registros mais antigos. Escritas serão serializadas e o custo de manutenção permanecerá proporcional ao pequeno limite de armazenamento, sem banco ou WorkManager.

### DiagnosticSnapshot

Coleta, no momento da exportação, o estado técnico observável:

- fabricante, modelo e versão do Android;
- versão e código do aplicativo;
- timestamp, fuso do dispositivo e Fuso da Conta;
- estado de `POST_NOTIFICATIONS`;
- resultado de `NotificationManager.areNotificationsEnabled()`;
- existência e importância do canal `planner-reminders`;
- disponibilidade de alarmes exatos;
- se o aplicativo ignora otimizações de bateria;
- intervalo, quantidade e descartes dos logs persistidos.

Estados não expostos de forma confiável, como a lista de suspensão profunda da Samsung e todas as exceções de Não perturbe, serão representados como `unavailable`. O README orientará a verificação manual.

### DiagnosticExporter

Produz uma fotografia consistente em arquivo temporário privado e cria um ZIP com:

- `events.jsonl` — eventos estruturados em ordem cronológica;
- `diagnostics.json` — snapshot técnico atual;
- `README.txt` — explicação do conteúdo, privacidade e verificações manuais.

O arquivo temporário será concluído antes de ser copiado ao destino escolhido pelo seletor nativo. Cancelamento ou falha não modifica os logs nem os dados do Planner.

## Eventos de lembrete

O ciclo completo será instrumentado com os seguintes eventos:

1. `reminder_schedule_requested`
2. `reminder_skipped_without_time`
3. `reminder_skipped_past`
4. `reminder_scheduled_exact`
5. `reminder_scheduled_inexact`
6. `reminder_cancelled`
7. `reminder_receiver_started`
8. `notification_published`
9. `notification_blocked_permission`
10. `notification_blocked_channel`
11. `notification_failed`

Os detalhes podem incluir horário pretendido, horário efetivo, Fuso da Conta e método de agendamento. Exceções serão reduzidas a classe e mensagem sanitizada. Stack traces completos não serão persistidos por padrão.

O `runCatching` silencioso do receiver será substituído por decisões observáveis. Antes de publicar, o código verifica permissão geral e canal; depois registra sucesso ou falha da chamada ao `NotificationManager`.

## Interface

A tela de Configurações receberá uma seção **Diagnóstico** com:

- descrição de que o ZIP contém informações técnicas e não contém dados pessoais do Planner;
- botão **Exportar logs**;
- indicador de geração em andamento;
- feedback de sucesso ou erro.

O fluxo usará `CreateDocument("application/zip")` e sugerirá o nome `planner-diagnostico-AAAA-MM-DD.zip`. Não será solicitada permissão de acesso amplo ao armazenamento.

## Plano de implementação TDD

### Fatia 1 — Log estruturado e retenção

Criar o contrato público de eventos, serialização JSONL, anonimização, arquivo privado e retenção.

Comportamentos prioritários:

- eventos são exportados na ordem em que foram aceitos;
- o mesmo identificador produz correlation ID estável somente naquela instalação;
- registros anteriores a sete dias são descartados;
- o conjunto permanece abaixo de 1 MB;
- gravações concorrentes não corrompem linhas;
- falha de I/O não escapa para o chamador.

### Fatia 2 — Instrumentação de lembretes

Integrar o logger ao planejamento, ao gateway de alarms e ao receiver, sem registrar títulos.

Comportamentos prioritários:

- cada caminho de agendamento registra sua decisão;
- itens sem horário ou no passado explicam por que foram ignorados;
- receiver e publicação compartilham correlation ID;
- permissão negada e canal bloqueado produzem eventos distintos;
- exceções deixam evidência sanitizada em vez de desaparecer.

### Fatia 3 — Snapshot técnico

Encapsular APIs Android em uma interface pequena e gerar um modelo serializável e versionado.

Comportamentos prioritários:

- estados permitido, negado, bloqueado e indisponível não são confundidos;
- relógio e fusos são registrados de maneira inequívoca;
- ausência do canal é diferente de canal com importância `NONE`;
- testes usam relógio e gateway do sistema controlados.

### Fatia 4 — Pacote ZIP

Gerar os três arquivos e validar o pacote como consumidor externo.

Comportamentos prioritários:

- ZIP abre e contém exatamente os arquivos documentados;
- conteúdo usa UTF-8 e formatos versionados;
- pacote vazio ainda contém snapshot e README úteis;
- nomes, títulos e IDs reais não aparecem no conteúdo;
- falha ou cancelamento não produz arquivo parcialmente válido.

### Fatia 5 — Configurações

Adicionar a ação pública de exportação e conectá-la ao seletor de documentos.

Comportamentos prioritários:

- botão inicia a exportação uma única vez;
- nome sugerido segue a data local;
- cancelamento retorna silenciosamente à tela;
- sucesso e erro apresentam feedback acionável;
- rotação ou recomposição não duplica a escrita.

## Validação final

- Executar todos os testes unitários.
- Compilar os testes Compose.
- Executar `assembleDebug`.
- No emulador, criar uma Tarefa futura, confirmar o alarm, disparar o receiver e exportar o ZIP.
- Correlacionar `schedule_requested`, método escolhido, `receiver_started` e resultado de publicação.
- Repetir com permissão de notificações negada e verificar `notification_blocked_permission`.
- Auditar o ZIP por nomes, títulos, IDs reais e conteúdo do banco.

## Fora de escopo

- envio automático ou remoto de logs;
- captura de Logcat completo;
- exportação do banco ou backup junto aos logs;
- alteração da política de alarmes exatos;
- bypass automático de Não perturbe;
- leitura de configurações privadas específicas de fabricantes.
