# Planner Android — sistema visual

## Direção

**Caderno de Porcelana**: um planner pessoal calmo e íntimo, com aparência de papel rosado e precisão de ferramenta. A interface deve parecer acolhedora ao abrir pela manhã, sem cair em rosa infantil ou decoração excessiva.

Pessoa central: alguém consultando rapidamente o próprio Dia, muitas vezes com uma mão e conectividade instável. O conteúdo do Dia deve ganhar do chrome.

Assinatura: **Fita do Dia**, uma faixa semanal em que a data selecionada se eleva como marcador físico e conduz visualmente ao progresso e ao conteúdo daquele Dia.

## Cor e superfícies

Estratégia de profundidade: somente mudanças sutis de superfície. Sem gradientes e sem sombras dramáticas.

### Claro

- `porcelain`: `#FFF8FB` — fundo principal.
- `petalPaper`: `#FFF0F6` — folha de conteúdo.
- `blushInset`: `#FFE2EE` — controles e seleção suave.
- `raspberry`: `#A83262` — ação, seleção e foco.
- `wineInk`: `#2B1821` — texto principal.
- `plumText`: `#755566` — texto secundário.
- `roseLine`: `#E5C4D1` — separação discreta.

### Escuro

- `nightPorcelain`: `#1B1116`.
- `nightPaper`: `#271920`.
- `nightBlush`: `#35212A`.
- `petalLight`: `#FF8FBA` — ação e foco.
- `nightInk`: `#FFE8F0`.
- `nightSecondary`: `#D8B8C5`.
- `nightLine`: `#5F3D4B`.

Rosa ocupa aproximadamente 10% da tela e comunica seleção, ação ou estado. Superfícies permanecem quase neutras, sempre com temperatura rosada.

## Hierarquia

- Foco da tela do Dia: data atual + itens planejados.
- Datas e números editoriais: Fraunces, peso 500, tracking levemente negativo.
- Controles, títulos e estados: Manrope, pesos 400 e 500.
- Escala Compose: 12sp metadado, 14sp apoio, 16sp corpo, 20sp seção, 28sp título e 36sp data hero.
- Quatro níveis de texto: principal, secundário, metadado e desabilitado.

## Geometria e ritmo

- Unidade base: 4dp.
- Espaçamento micro: 4–8dp; componente: 12–16dp; seção: 24dp; área principal: 32dp.
- Controle: raio 12dp e altura mínima 48dp.
- Folha/card: raio 20dp.
- Modal e contêiner principal: raio 28dp.
- Hit area mínima: 48x48dp.
- Fita do Dia: células de 52dp; data selecionada com 64dp e elevação geométrica, não sombra.

## Componentes recorrentes

- `PlannerPrimaryButton`: 52dp de altura, 16dp horizontal, raio 14dp, framboesa sólido.
- `PlannerTextField`: 56dp de altura mínima, fundo `blushInset`, borda apenas em foco/erro.
- `DayRibbon`: sete datas, segunda-feira primeiro, seleção elevada.
- `PlannerSheet`: superfície contínua; itens são linhas, não uma coleção de cards repetidos.
- `SyncStatus`: linha discreta no rodapé; offline informa segurança local, não falha catastrófica.

## Estados e movimento

- Todos os controles: normal, pressionado, foco, desabilitado e erro.
- Press feedback de 120ms; entradas de modal/drawer até 240ms; apenas `transform`/`alpha` equivalentes do Compose.
- Respeitar remoção de animações do sistema.
- Loading, vazio, erro e offline são estados de primeira classe.
- Erros usam texto explícito e ícone; nunca somente cor.

