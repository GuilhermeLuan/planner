# Política de sincronização offline-first

Status: substituída pela ADR 0023

O app Android grava e opera localmente, enfileirando alterações para sincronização; o backend mantém a visão canônica por Conta. A sincronização acontece ao abrir ou retomar o app, após alterações quando houver rede e por ação manual, sem tempo real no MVP. Quando alterações concorrentes chegam de dispositivos diferentes, a versão aceita mais recentemente pelo servidor vence, sem resolução manual de conflitos, para manter a experiência e o stack leves.
