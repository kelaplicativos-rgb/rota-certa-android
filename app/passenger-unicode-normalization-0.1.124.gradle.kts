// Normaliza nomes recebidos em Unicode decomposto pela acessibilidade.
val passengerPolicy124 = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RidePassengerIdentityPolicy.kt").asFile
var passengerPolicyText124 = passengerPolicy124.readText()
passengerPolicyText124 = passengerPolicyText124.replace(
    ".map(String::trim)",
    ".map { line -> Normalizer.normalize(line, Normalizer.Form.NFC).trim() }",
)
passengerPolicyText124 = passengerPolicyText124.replace(
    "val normalized = value.trim().replace(Regex(\"\\\\s+\"), \" \")",
    "val normalized = Normalizer.normalize(value, Normalizer.Form.NFC).trim().replace(Regex(\"\\\\s+\"), \" \")",
)
if ("Normalizer.Form.NFC" !in passengerPolicyText124) {
    throw GradleException("Normalizacao Unicode NFC do passageiro nao foi aplicada.")
}
passengerPolicy124.writeText(passengerPolicyText124)
