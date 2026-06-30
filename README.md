# Rota Certa

APK Android para analisar prints de chamadas de corrida e avisar se o destino final do passageiro fica dentro ou fora da area desejada pelo motorista.

O app apenas analisa e recomenda. Ele nao clica em outros aplicativos, nao aceita corrida sozinho e nao burla plataformas.

## Objetivo principal

A regra central do Rota Certa e simples:

- O motorista configura uma casa, localidade alternativa ou alfinete.
- O motorista define um raio maximo, por exemplo 10 km.
- O app le o print por OCR e tenta identificar o destino final do passageiro.
- O app calcula a distancia em linha reta entre o destino final e o ponto configurado.
- Se o destino final estiver dentro do raio, mostra VERDE / Dentro da area.
- Se o destino final passar do raio, mesmo por pouco, mostra VERMELHO / Fora da area.

Exemplo:

- Ponto configurado: casa do motorista.
- Raio: 10 km.
- Destino final a 8,7 km: VERDE.
- Destino final a 10,1 km: VERMELHO.

## MVP

- Kotlin
- Jetpack Compose
- OCR local com ML Kit Text Recognition
- Selecao de print pela galeria
- Deteccao da cidade/pais pela localizacao do aparelho
- Configuracao de casa, alfinete/localidade alternativa e raios em km
- Opcao de digitar local manualmente ou preencher pela localizacao GPS atual
- Salvamento de coordenadas GPS para calculo de raio mais preciso
- Historico local das analises
- Sem backend

## Regras de decisao

- Se o destino final nao for identificado: `Dados insuficientes`.
- Se o destino final for identificado, mas nao tiver coordenada confiavel: `Dados insuficientes`.
- Se o destino final estiver ate o raio configurado da casa: `VERDE - Dentro da area`.
- Se o destino final estiver ate o raio configurado do alfinete/localidade alternativa: `VERDE - Dentro da area`.
- Se o destino final estiver acima dos raios configurados: `VERMELHO - Fora da area`.
- Palavras evitadas continuam como filtro opcional auxiliar.

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

O projeto possui workflow do GitHub Actions para gerar o APK debug automaticamente.

Tambem e possivel gerar localmente com Gradle:

```bash
gradle assembleDebug
```

O APK fica em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Proximas etapas

1. Testar com 2 ou 3 prints reais.
2. Ajustar o parser para os layouts dos apps usados.
3. Adicionar monitoramento automatico da pasta Screenshots.
4. Avaliar MediaProjection para leitura em tempo real.
5. Avaliar API de rotas para distancia real em vez de linha reta.
