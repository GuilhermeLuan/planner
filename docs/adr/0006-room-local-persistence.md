# Persistência local com Room

O app Android usa Room sobre SQLite como banco local primário para o uso offline. A UI lê e grava nessa base; a sincronização com o backend trabalha a partir das mudanças locais pendentes, mantendo o domínio do cliente independente da API.
