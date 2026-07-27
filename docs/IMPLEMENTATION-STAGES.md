# Implementação segura por etapas

"
        "## Etapa 1 — fonte estável
"
        "- Materializar o código realmente compilado.
"
        "- Remover a aplicação automática dos patches Gradle.
"
        "- Garantir que dois builds consecutivos usem a mesma fonte.

"
        "## Etapa 2 — contrato do farol
"
        "- Somente pacotes escolhidos manualmente.
"
        "- Nenhum aplicativo predefinido.
"
        "- Nenhum modelo de card no caminho de decisão.
"
        "- Dois ou mais endereços visíveis; o último é o destino.

"
        "## Etapa 3 — organização da interface
"
        "- Botões de criar/salvar acima das listas.
"
        "- Alertas e locais salvos dentro de expanders fechados.
"
        "- Confirmação visível ao salvar.

"
        "## Etapa 4 — gestos e alertas
"
        "- Toque simples abre a grade.
"
        "- Toque duplo cria alerta.
"
        "- Toque fora fecha a grade.
"
        "- Fechar alerta silencia até sair da zona.

"
        "## Etapa 5 — limpeza e validação final
"
        "- Remover caminhos mortos e testes obsoletos.
"
        "- Testes, Lint, dois builds consecutivos e APK.
"
