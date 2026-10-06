# Entregas Android do Rota Certa

- Toda entrega ao usuário deve atualizar a instalação existente e preservar seus dados. Não use desinstalação, limpeza de dados ou downgrade como solução.
- Use a branch e o commit da versão instalada informados pelo usuário como base. A `main` histórica não representa automaticamente essa versão.
- Preserve `br.com.mapeiaia.rotacerta` e a chave estável configurada em `app/build.gradle.kts`. Não substitua a chave por uma nova chave debug gerada pelo ambiente.
- Cada nova versão deve ter `versionCode` maior que o último instalado e registro correspondente em `release_history.json`. Atualize `app/update-baseline.json` quando uma nova versão instalada for confirmada.
- Execute `verifyAndroidUpdateContract` e compare o APK final com o APK de referência usando `scripts/verify_apk_upgrade.py`. Bloqueie a entrega se pacote, assinatura, versão, origem ou integridade divergirem.
- Teste a atualização com `adb install -r`, preservando o UID e os dados privados, quando houver emulador/aparelho de teste disponível. Distingua teste em emulador de teste no Samsung do usuário.
- Não altere armazenamento ou migrações sem necessidade; quando necessário, teste a preservação dos dados existentes.
- Entregue somente o APK gerado do commit validado, com SHA-256 e evidências. Um build anterior ou APK apenas renomeado não é substituto.
