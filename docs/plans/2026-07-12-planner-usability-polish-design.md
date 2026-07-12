# Polimento de usabilidade do Planner

## Escopo

Este desenho cobre as issues #17 a #20: criação de Rotina responsiva e localizada, apresentação unificada de Tarefas, orientação temporal para o Dia atual e novo ícone Android.

## Criação de Rotina

A tela continua produzindo um `RoutineDraft`, sem alterar o modelo de domínio. A data inicial será apresentada e aceita como `DD/MM/AAAA`; uma pequena interface de formatação e interpretação esconderá a conversão para `LocalDate`. Erros de formato e datas inexistentes impedirão o salvamento com mensagem em português.

Os sete dias permanecerão simultaneamente visíveis. Em vez de uma linha rígida de chips, a seleção usará células compactas distribuídas igualmente pela largura disponível, de segunda-feira a domingo. A abertura continuará usando o Dia selecionado como data inicial e pré-selecionando seu dia da semana.

## Tarefas

O horário permanece opcional no domínio. A tela mostrará uma única seção `Tarefas`, com itens cronometrados em ordem crescente e itens sem horário em seguida. As ações de concluir, editar, reagendar e arquivar permanecem idênticas. Arquivadas continuam em sua seção própria.

## Dia atual

O Dia atual será calculado usando o Fuso da Conta e entregue à tela explicitamente. A seleção e o estado “hoje” serão representados separadamente: seleção usa preenchimento; hoje recebe um indicador persistente. Quando outra data estiver aberta, o cabeçalho deixa de dizer “HOJE” e oferece uma ação direta para voltar ao Dia atual. O calendário mensal também volta ao mês atual ao acionar essa ação.

## Ícone

A arte indicada na issue #20 será adaptada como ícone Android com fundo rosa do Planner. Recursos legados e adaptativos manterão o personagem na área segura das máscaras comuns. A validação combinará build do Android com inspeção visual das variantes circular e arredondada.

## Estratégia TDD

Cada issue será implementada como uma sequência independente de testes comportamentais. Formatação de data, ordenação de Tarefas e cálculo temporal ficarão atrás de interfaces pequenas e determinísticas. Estados de UI serão testados por modelos observáveis sempre que o teste Compose não acrescentar valor. Para o ícone, o build Android será o teste de integração da interface pública de recursos.
