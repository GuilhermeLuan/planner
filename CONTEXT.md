# Planner

Este contexto define a linguagem de um planner pessoal focado em organizar cada dia com rotinas recorrentes e tarefas pontuais.

Cada Planner pertence a uma Conta. No aplicativo Android local-only, cada instalação cria e restaura uma única Conta local, sem servidor ou autenticação remota.

## Contas

**Conta**:
Identidade privada de uma pessoa usuária, dona de um Planner e dos seus dados.
_Evitar_: usuário (quando o conceito for a identidade e seus dados), perfil

**Fuso da Conta**:
Configuração que define como os Dias, horários, recorrências e notificações são interpretados para uma Conta.
_Evitar_: fuso do servidor (quando o contexto for a experiência da Conta)

## Organização

**Planner**:
Espaço pessoal de planejamento que organiza a vida da pessoa por dias.
_Evitar_: agenda, calendário (quando o conceito for o espaço completo de planejamento)

**Dia**:
Recorte de uma data do planner que reúne o que a pessoa pretende acompanhar ou realizar naquele dia.
_Evitar_: página (quando o conceito for a data)

## Itens planejados

**Rotina**:
Atividade recorrente que aparece automaticamente nos dias da semana escolhidos, pode ter um horário opcional e continua ativa até ser pausada ou arquivada. Pode ser marcada como concluída em cada ocorrência.
_Evitar_: tarefa recorrente, evento

**Ocorrência de rotina**:
A aparição de uma Rotina em um Dia específico; pode ficar pendente, ser concluída ou ser pulada, e seu estado pertence somente àquele Dia.
_Evitar_: rotina, tarefa

**Tarefa**:
Atividade pontual planejada para um dia específico, independente de uma rotina, podendo ocupar o dia inteiro ou ter um horário definido e ser reagendada manualmente para outro Dia.
_Evitar_: compromisso, rotina

**Lembrete**:
Notificação entregue no horário de uma Tarefa, de uma Ocorrência de rotina ou de uma Dose, que a pessoa pode ignorar sem que nada mais aconteça.
_Evitar_: alarme, aviso

## Remédios

**Remédio**:
Medicamento que a pessoa acompanha no Planner, com dose, horários, dias de uso e Estoque opcional; continua ativo até ser arquivado.
_Evitar_: medicamento, medicação, rotina (quando o conceito for um remédio)

**Dose**:
A aparição de um Remédio em um Dia e horário específicos; pode ficar pendente, ser tomada ou ser pulada, e seu estado pertence somente àquela Dose.
_Evitar_: tomada, ocorrência (quando o conceito for de um Remédio)

**Alarme de Dose**:
Aviso insistente, em tela cheia e com som contínuo, que toca quando uma Dose continua pendente depois do Atraso do alarme; para quando a Dose é tomada, adiada ou pulada.
_Evitar_: lembrete, despertador

**Atraso do alarme**:
Intervalo, escolhido por Remédio, entre o Lembrete de uma Dose e o seu Alarme de Dose.
_Evitar_: soneca, tolerância

**Estoque**:
Quantidade restante de um Remédio, com um limite de aviso: quando a quantidade fica igual ou abaixo dele, a pessoa é avisada para repor.
_Evitar_: saldo, inventário

## Água

**Meta de água**:
Quantidade de água que a pessoa quer beber por Dia; uma nova meta vale dali em diante, sem alterar Dias passados.
_Evitar_: rotina de água, objetivo

**Consumo de água**:
Total de água registrado em um Dia, comparado com a Meta de água daquele Dia.
_Evitar_: progresso (quando o conceito for água), histórico

**Lembrete de água**:
Lembrete que se repete em um intervalo fixo dentro de uma janela de horário do Dia, enquanto o Consumo de água não alcança a meta.
_Evitar_: alarme de água
