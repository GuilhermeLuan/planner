# Painel web simples para administração

Status: substituída pela ADR 0023

O gerenciamento de Contas será feito por um painel web simples server-rendered pelo backend self-hosted, em vez de uma tela administrativa no app Android. O painel é restrito à Conta administradora; as demais Contas não terão acesso a ele no MVP. O app Android continua sendo o cliente principal do Planner; o painel existe para tarefas administrativas com menor frequência.

No MVP, o painel permite criar Contas, ativá-las ou desativá-las, redefinir senhas, consultar último acesso/última sincronização e encerrar a sessão administrativa. Ele não edita Planners nem oferece relatórios.
