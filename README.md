# Rota Certa

APK Android para analisar chamadas de corrida e avisar se o destino final do passageiro fica dentro ou fora da area desejada pelo motorista.

O app apenas analisa e recomenda. Ele nao clica em outros aplicativos, nao aceita corrida sozinho e nao burla plataformas.

## Objetivo principal

A regra central do Rota Certa e simples:

- O motorista configura uma casa, localidade alternativa ou alfinete.
- O motorista ajusta o raio maximo por uma barra deslizante, por exemplo 5 km ou 10 km.
- O app le a tela quando a corrida toca, usando Servico de Acessibilidade e OCR por screenshot quando necessario.
- O app tenta identificar o destino final do passageiro.
- Com Google Maps configurado, o app localiza o destino pela Geocoding API e calcula distancia por rota de carro pela Routes API.
- Se o destino final estiver dentro do raio, mostra VERDE / Dentro da area.
- Se o destino final passar do raio, mesmo por pouco, mostra VERMELHO / Fora da area.
- Se nao houver leitura confiavel, mostra AMARELO / Dados insuficientes.

Exemplo:

- Ponto configurado: casa do motorista.
- Raio: 10 km.
- Destino final a 8,7 km: VERDE.
- Destino final a 10,1 km: VERMELHO.

## Leitura ao vivo

Na aba `Analise`, toque em `Ativar leitura ao vivo` e ative `Rota Certa - leitura ao vivo` nas configuracoes de Acessibilidade do Android.

Com o servico ativo, o app faz varredura rapida da tela:

- tenta ler textos expostos pela acessibilidade;
- tira screenshot pelo proprio Servico de Acessibilidade em Android compatível;
- roda OCR local com ML Kit;
- mostra uma bolinha por cima da tela:
  - verde: dentro do raio;
  - vermelho: fora do raio;
  - amarelo: lendo ou dados insuficientes.

A leitura por print/manual e o radar por print continuam como segunda opcao.

## Raio rapido

Na aba `Analise`, use `Raio rapido` para alterar o raio da casa e do alfinete sem entrar nas configuracoes.

- A barra vai de 1 km ate 30 km.
- O ajuste e feito em passos de 0,5 km.
- Ao soltar a barra, o valor ja fica salvo para as proximas leituras.

## MVP

- Kotlin
- Jetpack Compose
- OCR local com ML Kit Text Recognition
- Leitura ao vivo por Servico de Acessibilidade
- OCR por screenshot de acessibilidade quando o card nao expõe texto suficiente
- Selecao de print pela galeria
- Deteccao da cidade/pais pela localizacao do aparelho
- Configuracao de casa, alfinete/localidade alternativa e raios em km
- Opcao de digitar local manualmente ou preencher pela localizacao GPS atual
- Salvamento de coordenadas GPS para calculo de raio mais preciso
- Google Maps Geocoding API para localizar enderecos com mais confiabilidade
- Google Maps Routes API para distancia real por rota de carro
- Historico local das analises
- Sem backend

## Regras de decisao

- Se o destino final nao for identificado: `Dados insuficientes`.
- Se o destino final for identificado, mas nao tiver coordenada confiavel: `Dados insuficientes`.
- Se a chave Google Maps nao estiver configurada: `Dados insuficientes`.
- Se o destino final estiver ate o raio configurado da casa: `VERDE - Dentro da area`.
- Se o destino final estiver ate o raio configurado do alfinete/localidade alternativa: `VERDE - Dentro da area`.
- Se o destino final estiver acima dos raios configurados: `VERMELHO - Fora da area`.
- Palavras evitadas continuam como filtro opcional auxiliar.

## Configuracao de localizacao

Na aba `Config`, o usuario pode escolher entre duas formas de configurar a casa e a localidade alternativa:

1. Digitar o endereco manualmente.
2. Tocar em `Usar GPS atual` / `Usar GPS` para preencher o endereco aproximado com a localizacao atual do aparelho.

Quando a opcao de GPS e usada, o app salva latitude e longitude junto com o endereco aproximado. Na analise da corrida, essas coordenadas salvas sao priorizadas em relacao ao texto digitado, deixando o calculo de raio mais confiavel.

## Google Maps API

Na aba `Config`, o campo `Chave Google Maps API` e necessario para decisao verde/vermelha por rota real.

Com chave configurada, o app usa:

- `Geocoding API` para transformar o destino final extraido da tela/print em latitude/longitude.
- `Routes API` para calcular a distancia real de carro entre o destino final e a casa/alfinete.

Para usar:

1. Crie uma chave no Google Cloud / Google Maps Platform.
2. Ative a `Geocoding API` e a `Routes API`.
3. Cole a chave no campo `Chave Google Maps API` dentro do app ou configure o secret `GOOGLE_MAPS_API_KEY` no GitHub.
4. Toque em `Salvar configuracoes`.

## Permissoes Android

- `ACCESS_FINE_LOCATION` e `ACCESS_COARSE_LOCATION`: detectar cidade/pais do usuario, preencher localidade por GPS quando solicitado e calcular regioes com mais contexto.
- `INTERNET`: permitir chamadas ao Google Maps.
- `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`: compatibilidade com leitura de imagens e radar por print.
- `SYSTEM_ALERT_WINDOW`: bolinha do radar por print.
- Servico de Acessibilidade: leitura ao vivo da tela e bolinha de decisao.

## Limitacoes importantes

- OCR pode errar textos pequenos, cortados, com baixa resolucao ou sobrepostos.
- Apps de corrida mudam layout; o parser precisa evoluir com prints reais.
- A leitura ao vivo depende da permissao manual de Acessibilidade no Android.
- A varredura rapida prioriza velocidade no momento da corrida e pode consumir mais bateria enquanto estiver ativa.
- A precisao depende da qualidade do endereco extraido e da resposta das APIs do Google Maps.
- O app nao aceita corrida sozinho; apenas informa a cor de decisao.

## Como gerar APK pelo celular

O projeto possui workflow do GitHub Actions para gerar o APK debug automaticamente, sem computador.

1. Abra o repositorio no navegador do celular.
2. Entre em `Actions`.
3. Toque em `Build Debug APK`.
4. Abra a execucao mais recente.
5. Aguarde ficar verde.
6. Baixe o artefato `rota-certa-debug-apk`.
7. Extraia o ZIP baixado.
8. Instale o `app-debug.apk` no Android.

Se o Android bloquear a instalacao, permita instalacao de apps desconhecidos para o navegador ou gerenciador de arquivos usado.

## Como gerar APK pelo computador

Tambem e possivel gerar localmente com Gradle:

```bash
gradle assembleDebug
```

O APK fica em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Proximas etapas

1. Testar a leitura ao vivo dentro dos apps 99 Motorista, Uber Driver e inDrive.
2. Ajustar parser com novos prints reais quando algum layout mudar.
3. Avaliar backend proprio para proteger a chave Google em versao publica.
