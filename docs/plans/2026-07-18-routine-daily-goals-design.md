# Metas diárias acumulativas em Rotinas

## Objetivo

Permitir que uma Rotina acompanhe opcionalmente uma quantidade diária. Uma Rotina “Água” pode ter meta de 2.000 ml e incremento padrão de 500 ml; cada toque soma ao Progresso da Ocorrência do Dia selecionado.

## Comportamento

Rotinas simples preservam o checkbox atual. Rotinas com meta exibem total, meta, unidade, barra determinada e o incremento padrão. Também permitem adicionar outro valor ou ajustar o total. Alcançar ou ultrapassar a meta conclui a ocorrência automaticamente; ajustar abaixo da meta volta a deixá-la pendente.

O tipo da Rotina é escolhido na criação e não pode ser convertido na edição. Meta, unidade e incremento editados passam a valer para todos os Dias projetados, sem alterar os totais já persistidos.

## Persistência e integrações

A definição da Rotina guarda meta, unidade e incremento opcionais. A Ocorrência guarda somente o total acumulado, sem histórico de lançamentos. Atualizações são transacionais para não perder toques concorrentes.

Lembretes de Rotinas com meta oferecem o incremento padrão no lugar de “Concluir”. O backup versão 2 inclui a configuração da meta e o progresso das ocorrências. A migração Room 5→6 preserva Rotinas existentes como simples e inicializa o progresso com zero.

## Validação

- testes do repositório para soma, ajuste, conclusão, edição, concorrência e independência entre Dias;
- teste da migração 5→6 e do backup versão 2;
- testes de lembrete para ação e rótulo do incremento;
- testes Compose para criação, progresso, incremento, ajuste e edição da meta.
