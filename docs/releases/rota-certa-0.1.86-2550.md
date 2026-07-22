# Rota Certa 0.1.86 (2550)

Commit: `9b7db410d94526a0bf9d25166f2823088bf1053b`

## Correções baseadas no relatório 0.1.85

- O ciclo de card visível só começa depois de confirmar um card individual cadastrado.
- Snapshots parciais não geram mais centenas de eventos `VisibleCard` com `hash=-1`, `card=null` e `contract=null`.
- A transação do pipeline fica vinculada ao hash do snapshot, evitando mistura entre eventos de acessibilidade e OCR concorrentes.
- A assinatura estável do card inclui pacote, modelo, destino final e valor.
- Leitura de popup/print é descartada quando a raiz atual é DocumentsUI, galeria, seletor de fotos ou outro pacote passivo.
- Resultados de screenshot/OCR antigos não podem reaparecer depois que o usuário sai do aplicativo de corrida.
- O patch de lifecycle ficou idempotente para testes e montagem do APK executados em sequência.
- Publicação do asset estável foi endurecida para atualizar um tag já existente sem tentar retargetá-lo.

## Validação

- GitHub Actions run: `29292529009`
- Run number: `1550`
- Testes unitários: sucesso
- Build debug APK: sucesso
- Validação do APK: sucesso
- Upload do APK: sucesso
- Publicação estável: sucesso
- APK SHA-256: `53d7cf30e244b7683f4a3922eb12f20a426f75409541b89caa0c1298981ca477`
- Tamanho: `55,566,299 bytes`

## Downloads

- Stable: https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/rota-certa-stable-debug/rota-certa-stable-debug.apk
- Artifact: https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/29292529009/artifacts/8295618378
