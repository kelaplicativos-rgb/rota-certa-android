# Rota Certa

APK Android para analisar chamadas de corrida e avisar se o destino final do passageiro fica dentro ou fora da area desejada pelo motorista.

O app apenas analisa e recomenda. Ele nao clica em outros aplicativos, nao aceita corrida sozinho e nao burla plataformas.

## Objetivo principal

A regra central do Rota Certa e simples:

- O motorista configura uma casa, localidade alternativa ou alfinete.
- O motorista ajusta o raio maximo por uma barra deslizante, por exemplo 5 km ou 10 km.
- O app le a tela quando a corrida toca, usando Servico de Acessibilidade e OCR por screenshot quando necessario.
- O app tenta identificar somente o destino final do passageiro.
- O app calcula a distancia entre esse destino final e a casa/alfinete configurado pelo motorista.
- Com Google Maps configurado, o app usa Geocoding API e Routes API para melhorar a precisao por rota de carro.
- Sem Google Maps ou sem retorno de rota, o app usa distancia aproximada entre coordenadas quando elas estiverem disponiveis.
- Se o destino final estiver dentro do raio, mostra VERDE / Dentro da area.
- Se o destino final passar do raio, mesmo por pouco, mostra VERMELHO / Fora da area.
- Se nao houver leitura confiavel, mostra AMARELO / Dados insuficientes.

## Operacao principal

A aba `Analise` e focada em uso real durante a corrida:

- botao `ON - leitura ao vivo ativa` quando a acessibilidade esta liberada;
- botao `OFF - permitir acessibilidade` quando o acesso ainda esta negado;
- ajuste rapido do raio por barra;
- preferencias da bolinha: transparencia e cor mais escura;
- ultimo resultado detectado pela leitura ao vivo.

As opcoes de selecionar print manualmente e radar por print foram retiradas da tela principal para evitar confusao.

## Bolinha

Com o servico ativo, o app mostra uma bolinha por cima da tela:

- verde: destino final dentro do raio configurado;
- vermelho: destino final fora do raio configurado;
- amarelo: tela alterada, corrida ausente, leitura em andamento ou dados insuficientes.

A bolinha tambem permite:

- tocar para abrir o Rota Certa diretamente;
- arrastar para escolher a melhor posicao;
- ajustar transparencia;
- usar cor normal ou mais escura;
- mostrar a distancia do destino final ate a casa/alfinete, por exemplo `10km`.

Quando a tela muda, o card some ou outro card aparece, a bolinha volta para amarelo e uma nova leitura e feita.

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
- OCR por screenshot de acessibilidade quando o card nao expoe texto suficiente
- Deteccao da cidade/pais pela localizacao do aparelho
- Configuracao de casa, alfinete/localidade alternativa e raios em km
- Opcao de digitar local manualmente ou preencher pela localizacao GPS atual
- Salvamento de coordenadas GPS para calculo de raio mais preciso
- Google Maps Geocoding API para localizar enderecos com mais confiabilidade
- Google Maps Routes API para distancia real por rota de carro
- Fallback por distancia aproximada quando a rota real nao estiver disponivel
- Historico local das analises
- Sem backend

## Regras de decisao

- Se o destino final nao for identificado: `Dados insuficientes`.
- Se o destino final for identificado, mas nao tiver coordenada confiavel: `Dados insuficientes`.
- Se houver coordenada do destino final e da casa/alfinete, mas nao houver rota real, o app usa distancia aproximada.
- Se o destino final estiver ate o raio configurado da casa: `VERDE - Dentro da area`.
- Se o destino final estiver ate o raio configurado do alfinete/localidade alternativa: `VERDE - Dentro da area`.
- Se o destino final estiver acima dos raios configurados: `VERMELHO - Fora da area`.
- Palavras evitadas continuam como filtro opcional auxiliar, aplicadas somente ao destino final.

## Configuracao de localizacao

Na aba `Config`, o usuario pode escolher entre duas formas de configurar a casa e a localidade alternativa:

1. Digitar o endereco manualmente.
2. Tocar em `Usar GPS atual` / `Usar GPS` para preencher o endereco aproximado com a localizacao atual do aparelho.

Quando a opcao de GPS e usada, o app salva latitude e longitude junto com o endereco aproximado. Na analise da corrida, essas coordenadas salvas sao priorizadas em relacao ao texto digitado, deixando o calculo de raio mais confiavel.

## Google Maps API

Na aba `Config`, o campo `Chave Google Maps API` melhora a precisao do calculo por rota real.

Com chave configurada, o app usa:

- `Geocoding API` para transformar o destino final extraido da tela em latitude/longitude.
- `Routes API` para calcular a distancia real de carro entre o destino final e a casa/alfinete.

Sem chave, ou quando a API nao retornar rota, o app pode decidir por distancia aproximada quando houver coordenadas confiaveis.

Para usar a rota real:

1. Crie uma chave no Google Cloud / Google Maps Platform.
2. Ative a `Geocoding API` e a `Routes API`.
3. Cole a chave no campo `Chave Google Maps API` dentro do app ou configure o secret `GOOGLE_MAPS_API_KEY` no GitHub.
4. Toque em `Salvar configuracoes`.

## Permissoes Android

- `ACCESS_FINE_LOCATION` e `ACCESS_COARSE_LOCATION`: detectar cidade/pais do usuario, preencher localidade por GPS quando solicitado e calcular regioes com mais contexto.
- `INTERNET`: permitir chamadas ao Google Maps.
- Servico de Acessibilidade: leitura ao vivo da tela, OCR por screenshot e bolinha de decisao.

## Prontidao para Play Store

O projeto atual esta apto para testes por APK debug, mas ainda nao deve ser considerado pronto para publicacao aberta na Play Store sem os itens abaixo:

1. Criar AAB release assinado, nao apenas APK debug.
2. Configurar assinatura de app e secrets de release no GitHub Actions.
3. Adicionar uma tela de divulgacao clara antes de abrir as configuracoes de Acessibilidade.
4. Exigir consentimento afirmativo do usuario para a leitura ao vivo.
5. Preparar Politica de Privacidade explicando leitura de tela, OCR, localizacao e uso da chave Google Maps.
6. Preencher a declaracao de Acessibilidade no Play Console.
7. Gravar video de revisao mostrando abertura do app, divulgacao, aceite, ativacao da acessibilidade e uso da bolinha.
8. Publicar primeiro em teste interno/fechado antes da producao.

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
3. Adicionar a divulgacao e consentimento de Acessibilidade antes de publicar na Play Store.
4. Criar workflow de AAB release assinado.
5. Avaliar backend proprio para proteger a chave Google em versao publica.
