# Alarmes de Dose com SCHEDULE_EXACT_ALARM

Status: aceita

Um Remédio pode ter um Alarme de Dose que toca, em tela cheia e com som contínuo, quando a Dose continua pendente depois do Atraso do alarme. Para isso o Android precisa de alarmes exatos. Escolhemos a permissão `SCHEDULE_EXACT_ALARM`, que a pessoa concede em "Alarmes e lembretes", em vez de `USE_EXACT_ALARM`, que é concedida automaticamente. A política do Google Play reserva `USE_EXACT_ALARM` a apps de despertador e calendário, e queremos manter aberta a publicação na loja, mesmo que hoje o APK seja instalado direto.

Os horários são agendados com `AlarmManager.setAlarmClock()`, que o sistema nunca adia, nem mesmo em Doze. A tela de alarme usa full-screen intent. O som contínuo roda em um foreground service do tipo `systemExempted`, permitido para apps que têm a permissão de alarme exato.

## Consequências

- No Android 14+, a permissão começa negada em instalações novas. O app explica o motivo e abre `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` quando a pessoa ativa o primeiro Alarme de Dose. Se ela recusar, o Remédio é salvo e só o Lembrete é entregue.
- A permissão pode ser revogada a qualquer momento, e o sistema cancela os alarmes exatos sem avisar o app. Por isso o app confere `canScheduleExactAlarms()` sempre que abre, mostra os alarmes como pausados enquanto ela faltar e reagenda tudo ao receber `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. Também reagenda depois de `BOOT_COMPLETED`.
- `USE_FULL_SCREEN_INTENT` só é garantida para apps de chamada e alarme. O app confere `canUseFullScreenIntent()`. Sem ela, o Alarme de Dose vira uma notificação de alta prioridade com som de alarme.
- Os Lembretes de Tarefas e Rotinas, que hoje caem no modo inexato porque o manifesto não declara permissão de alarme exato, passam a ser exatos quando a permissão é concedida.
