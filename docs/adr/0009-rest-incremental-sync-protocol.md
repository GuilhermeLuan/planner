# Protocolo REST de sincronização incremental

Status: substituída pela ADR 0023

Android e backend se comunicam por REST/JSON. A sincronização envia operações locais pendentes com IDs idempotentes e baixa mudanças usando um cursor desde a última sincronização; retries são seguros e não dependem de conexão em tempo real.
