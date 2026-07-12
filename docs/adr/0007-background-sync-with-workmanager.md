# Sincronização em segundo plano com WorkManager

Status: substituída pela ADR 0023

O cliente Android agenda a sincronização por WorkManager, respeitando disponibilidade de rede e usando novas tentativas com backoff. Não haverá serviço permanente nem conexão em tempo real no MVP, mantendo consumo e operação offline previsíveis.
