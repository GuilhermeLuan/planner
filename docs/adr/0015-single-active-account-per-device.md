# Uma Conta ativa por dispositivo

Status: substituída pela ADR 0023

O app Android mantém uma única Conta ativa por dispositivo. A troca exige logout e novo login, e o banco local mantém dados separados por Conta, evitando mistura de planners e simplificando o escopo offline-first do MVP.
