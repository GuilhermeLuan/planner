# Suporte a HTTP e HTTPS

Status: substituída pela ADR 0023

O app Android aceita URLs HTTP e HTTPS para permitir instalações self-hosted locais sem certificado. Como HTTP expõe credenciais e dados em trânsito, o cliente deve exibir um aviso claro ao conectar por HTTP; HTTPS continua sendo a opção recomendada para servidores acessíveis pela rede.
