# Configuração Android e sessão da Conta

Status: complete

## What to build

Entregar o primeiro fluxo do cliente Android para configurar uma instalação e iniciar uma sessão de Conta. A pessoa informa a URL base do servidor, recebe um aviso explícito quando ela usa HTTP, autentica com credenciais locais, baixa a identidade e o Planner inicial, troca uma senha temporária quando necessário e passa a abrir o app com a sessão local protegida pelo Android Keystore.

O dispositivo deve manter uma única Conta ativa. Logout deve limpar o acesso local da sessão e permitir novo login com outra Conta sem misturar os Planners.

## Acceptance criteria

- [x] A primeira execução solicita e persiste uma URL de servidor válida, aceitando HTTP e HTTPS.
- [x] O app mostra um aviso claro antes de enviar credenciais por HTTP.
- [x] Login válido baixa a Conta e o Planner inicial; credenciais inválidas ou Conta desativada produzem erro acionável.
- [x] Uma Conta com senha temporária é direcionada à troca obrigatória antes de continuar para o uso normal.
- [x] Token e segredos de sessão são armazenados no Android Keystore e não em texto puro no armazenamento comum.
- [x] O app consegue abrir a sessão inicial sem rede após o bootstrap conectado.
- [x] Logout encerra a sessão local e a troca de Conta não mistura dados de Planners.
- [x] Testes de contrato do endpoint e testes instrumentados do Compose cobrem configuração, alerta HTTP, login, troca de senha, bootstrap offline, logout e Conta única ativa.

## Blocked by

- 01-bootstrap-servico-persistencia.md
- 02-painel-ciclo-contas.md
