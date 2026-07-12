# Planner

Planner pessoal local-only para Android, com rotinas recorrentes, tarefas pontuais, notificações e backup em JSON.

## Arquitetura vigente

- `android/` — Kotlin, Jetpack Compose e Room.
- Room é a fonte canônica da Conta e do Planner.
- Notificações são agendadas localmente no dispositivo.
- Não há servidor, login remoto, sincronização ou dependência de rede.
- `docs/adr/0023-local-only-android-planner.md` registra a decisão vigente.

## Executar os testes

```sh
cd android
./gradlew testDebugUnitTest
```

## Gerar o APK

```sh
cd android
./gradlew assembleDebug
```

O APK é criado em `android/app/build/outputs/apk/debug/app-debug.apk`.

## Backup

Em Configurações, use **Exportar backup** para salvar Conta, Planner, Tarefas, Rotinas e Ocorrências em um arquivo JSON.
