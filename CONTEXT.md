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
