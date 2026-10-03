# Planner

Planner pessoal local-only para Android, com rotinas recorrentes, tarefas pontuais, notificações e backup em JSON.

## Arquitetura vigente

- `android/` — Kotlin, Jetpack Compose e Room.
- Room é a fonte canônica da Conta e do Planner.
- Notificações são agendadas localmente no dispositivo.
- Não há servidor, login remoto, sincronização ou dependência de rede.
- `docs/adr/0023-local-only-android-planner.md` registra a decisão vigente.

## Subir emulador e compilar

### Iniciar o emulador Android

1. Abra o Android Studio
2. Vá em **Device Manager** (lado direito)
3. Selecione um emulador e clique no botão de play ▶️

Ou via CLI:
```sh
emulator -avd <nome_do_emulador>
```

Para listar emuladores disponíveis:
```sh
emulator -list-avds
```

### Compilar e instalar no emulador

```sh
cd android
./gradlew installDebug
```

Este comando compila o projeto, gera o APK e instala automaticamente no emulador conectado.

### Iniciar a app no emulador

Após a instalação, abra a app diretamente no emulador ou via CLI:
```sh
adb shell am start -n dev.guilhermeluan.planner/dev.guilhermeluan.planner.MainActivity
```

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
