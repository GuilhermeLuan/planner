# Ciclo de vida das Contas no painel administrativo

Status: complete

## What to build

Entregar o painel web server-rendered que permite à Conta administradora gerenciar o acesso à instalação sem visualizar nem editar Planners. A Conta administradora deve conseguir iniciar sessão, consultar Contas e seus metadados operacionais, criar uma Conta com senha temporária e Fuso da Conta, ativar ou desativar acesso, redefinir senha e encerrar a própria sessão.

O comportamento deve ser protegido tanto no fluxo HTML quanto nas operações de backend: não há autocadastro, Contas comuns não entram no painel e dados de Planner não aparecem nas respostas ou telas administrativas.

## Acceptance criteria

- [x] Somente uma sessão válida de Conta administradora acessa as páginas e ações administrativas.
- [x] O painel lista Contas com estado, último acesso e última sincronização sem exibir conteúdo de Planner.
- [x] A Conta administradora consegue criar uma Conta com nome de usuário, senha temporária e Fuso da Conta; nomes duplicados são rejeitados de forma legível.
- [x] A Conta administradora consegue ativar, desativar e redefinir a senha de uma Conta, com a troca de senha marcada como obrigatória após redefinição.
- [x] Contas desativadas não conseguem iniciar novas sessões.
- [x] Logout invalida a sessão administrativa e o painel não fica acessível por reutilização da sessão.
- [x] Testes HTTP/HTML cobrem autorização, criação, duplicidade, listagem, alterações de estado, redefinição, logout e ausência de dados de Planner.

## Blocked by

- 01-bootstrap-servico-persistencia.md
