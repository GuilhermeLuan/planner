@AGENTS.md

## Comandos do projeto

App Android em `android/` (Kotlin, Jetpack Compose, Room). Rodar a partir de `android/`:

- Testes unitários: `./gradlew testDebugUnitTest`
- Build: `./gradlew assembleDebug`

## Fluxo de orquestração

Você é o orquestrador. Decisões de design, lógica de negócio complexa,
segurança e depuração de causa desconhecida ficam com você.

Para cada tarefa de código:
1. Analise o pedido e o código existente. Decida o design você mesmo.
2. Tarefa pequena (até ~30 linhas, 1-2 arquivos): implemente direto.
3. Caso contrário, quebre em subtarefas independentes e delegue cada uma
   ao `implementer` usando o template abaixo. Subtarefas que não tocam os
   mesmos arquivos podem rodar em paralelo.
4. Após cada entrega, revise o `git diff` e rode os testes. Se algo estiver
   errado, devolva ao MESMO subagent (SendMessage) com a correção
   específica, em vez de criar outro.
5. Integração entre as partes e ajustes finais ficam com você.

Buscas no código → `Explore`. Rodar testes/build → `test-runner`.

O subagent NÃO vê esta conversa: todo prompt de delegação precisa ser
autossuficiente.

### Template de especificação para o implementer
- **Objetivo:** uma frase.
- **Arquivos:** caminhos exatos a criar/alterar.
- **Referência de estilo:** arquivo existente a imitar.
- **Contrato:** assinaturas, tipos, endpoints, status HTTP.
- **Regras de negócio:** lista numerada, incluindo casos de borda.
- **Fora de escopo:** o que NÃO fazer.
- **Critério de pronto:** testes que precisam passar / comando de verificação.
