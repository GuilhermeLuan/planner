# Proteção local com Android Keystore

Tokens e segredos da sessão ficam protegidos pelo Android Keystore; os dados do Room dependem da criptografia padrão do armazenamento do dispositivo. SQLCipher não será incluído no MVP, mantendo o app leve enquanto ainda evita guardar credenciais em texto puro.
