# Android autossuficiente e Planner local-only

Status: aceita

O aplicativo Android é autossuficiente e usa o Room como fonte canônica. Uma instalação cria uma única identidade local e seu Planner a partir do nome da pessoa e do fuso sugerido pelo dispositivo. A identidade é restaurada diretamente nas próximas aberturas, sem servidor, rede, senha, token ou sessão remota.

IDs são gerados localmente. Conta, Planner e seleção da identidade local são persistidos atomicamente. Dias, horários, recorrências e notificações usam o Fuso da Conta persistido.

Esta decisão substitui as ADRs 0001, 0002, 0003, 0004, 0007, 0008, 0009, 0012, 0013, 0014, 0015, 0016, 0017, 0018, 0019, 0020 e 0021. As ADRs 0005, 0006, 0010, 0011 e 0022 continuam válidas naquilo que não pressupõe backend ou sincronização.

Artefatos legados de backend e sincronização podem ser removidos incrementalmente, mas não participam do runtime local-only nem recebem novas operações.
