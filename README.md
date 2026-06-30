# Rota Certa

APK Android para analisar prints de chamadas de corrida e recomendar se a corrida faz sentido pelos raios configurados.

O app apenas analisa e recomenda. Ele nao clica em outros aplicativos, nao aceita corrida sozinho e nao burla plataformas.

## MVP

- Kotlin
- Jetpack Compose
- OCR local com ML Kit Text Recognition
- Selecao de print pela galeria
- Deteccao da cidade/pais pela localizacao do aparelho
- Configuracao de endereco da casa, localidade alternativa e raios em km
- Opcao de digitar local manualmente ou preencher pela localizacao GPS atual
- Salvamento de coordenadas GPS para calculo de raio mais preciso
- Palavras-chave desejadas e evitadas
- Historico local das analises
- Sem backend

## Regras de decisao

- Se o embarque estiver ate o raio configurado da casa: `Boa corrida`.
- Se o embarque estiver ate o raio configurado da localidade alternativa: `Boa corrida`.
- Se o embarque nao for identificado com confianca: `Dados insuficientes`.
- Se o embarque estiver fora dos dois raios: `Fora do raio`.
- Se encontrar palavra-chave evitada: `Fora do raio`.

## Configuracao de localizacao

Na aba `Config`, o usuario pode escolher entre duas formas de configurar a casa e a localidade alternativa:

1. Digitar o endereco manualmente.
2. Tocar em `Usar GPS atual` / `Usar GPS` para preencher o endereco aproximado com a localizacao atual do aparelho.

Quando a opcao de GPS e usada, o app salva latitude e longitude junto com o endereco aproximado. Na analise da corrida, essas coordenadas salvas sao priorizadas em relacao ao texto digitado, deixando o calculo de raio mais confiavel.

## Permissoes Android

- `ACCESS_FINE_LOCATION` e `ACCESS_COARSE_LOCATION`: detectar cidade/pais do usuario, preencher localidade por GPS quando solicitado e calcular regioes com mais contexto.
- `INTERNET`: permitir geocoding do Android quando o provedor do aparelho precisar consultar rede.
- `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`: compatibilidade com leitura de imagens, embora o Photo Picker moderno normalmente nao exija permissao ampla de armazenamento.

## Limitacoes importantes

- OCR pode errar textos pequenos, cortados, com baixa resolucao ou sobrepostos.
- Apps de corrida mudam layout; o parser precisa evoluir com prints reais.
- Geocoding por texto pode confundir ruas iguais em cidades diferentes; por isso o app usa cidade/pais detectados como contexto.
- Distancia inicial e calculada em linha reta. Distancia por rota real exige API de mapas/rotas.
- Monitoramento automatico de screenshots e captura de tela via MediaProjection ficam para etapas futuras.
- Servico de Acessibilidade, se usado no futuro, deve ser limitado a leitura de texto visivel e com consentimento claro do usuario.

## Como gerar APK

1. Abra o projeto no Android Studio.
2. Aguarde o sync do Gradle.
3. Conecte um celular ou use emulador.
4. Rode `app` em modo debug.
5. Para APK debug, use:

```bash
./gradlew assembleDebug
```

O APK fica em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Se o projeto for aberto sem Gradle Wrapper, use o Gradle do Android Studio ou gere o wrapper pelo proprio Android Studio.

## Proximas etapas

1. Testar com 2 ou 3 prints reais.
2. Ajustar o parser para os layouts dos apps usados.
3. Adicionar monitoramento automatico da pasta Screenshots.
4. Avaliar MediaProjection para leitura em tempo real.
5. Avaliar API de rotas para distancia real em vez de linha reta.

Build artifact trigger: 2026-06-30 22:50 UTC.
