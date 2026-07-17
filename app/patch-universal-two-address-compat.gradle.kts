// Compatibilidade final do leitor universal:
// - mantem a trava de idempotencia;
// - mantem os metadados e a sonda de validacao;
// - nao aplica filtro de pacote;
// - nao exige modelo de card cadastrado.
//
// O contrato ativo volta a ser: ler qualquer tela, localizar pelo menos dois
// enderecos completos e numerados, usar o ultimo como destino e calcular ate o
// endereco definido pelo usuario.
apply(from = "patch-universal-two-address-idempotence.gradle.kts")
apply(from = "universal-overlay-runtime-metadata.gradle.kts")
apply(from = "in-app-bubble-immediate-state.gradle.kts")
apply(from = "universal-runtime-state-probe.gradle.kts")
apply(from = "universal-immediate-gray-clear.gradle.kts")
apply(from = "universal-runtime-stability-guard.gradle.kts")
