# Coletor público BlaBlaCar

Ferramenta isolada do APK do Rota Certa para validar buscas públicas da BlaBlaCar por rota e data exatas.

## Estratégia validada

O coletor abre a página pública real da BlaBlaCar em Chromium normal, dentro de um display virtual do GitHub Actions, e lê os cartões já renderizados na própria página.

Não depende da API privada de busca. O caminho antigo que aguardava `GET /trip/search/v9` foi descartado porque o runner pode receber bloqueio antes dessa chamada. O navegador normal renderizado demonstrou funcionamento com HTTP 200 e foi validado tanto com busca zerada quanto com buscas contendo motoristas.

## Dados coletados

- rota pesquisada;
- data exata;
- horário de cada cartão;
- nome real do motorista;
- origem e destino reais mostrados no cartão;
- preço quando disponível;
- sinais `Cheio` e `Esgotará em breve`;
- status de perfil como `Super Driver` e `Perfil Verificado`, sempre separados do nome;
- presença nominal exata de `Ezequiel S`;
- presença nominal exata de `Barbosa`.

A rota pesquisada e a origem/destino reais do cartão são mantidas separadas porque a BlaBlaCar pode exibir caronas compatíveis que começam ou terminam em cidades próximas.

## Validação

`status: validated` só é emitido quando:

1. a página pública responde com sucesso;
2. a data final corresponde à data solicitada;
3. a origem final corresponde à origem solicitada;
4. o destino final corresponde ao destino solicitado;
5. a rota aparece na página;
6. existe conteúdo comprovável: cartões de motoristas ou mensagem explícita de zero viagens.

Em bloqueio HTTP, erro de navegador ou conteúdo não comprovado, o coletor retorna `status: error` ou `status: mismatch`. Resultado antigo nunca deve ser tratado como validação da nova consulta.

## Corredor pré-resolvido

`collector/places/corridor.json` contém os identificadores necessários para:

- Santo André/SP;
- São Paulo/SP;
- Extrema/MG;
- Camanducaia/MG;
- Pouso Alegre/MG;
- Três Corações/MG;
- Varginha/MG;
- Campanha/MG;
- Cambuquira/MG;
- São Thomé das Letras/MG.

Essas cidades não dependem de geocodificação em cada execução. Para cidades fora dessa tabela, o coletor pode usar `GOOGLE_MAPS_API_KEY` como fallback.

## Entrada

Arquivo: `collector/requests/current.json`

Exemplo:

```json
{
  "request_id": "tres-coracoes-santo-andre-2026-08-25",
  "from": "Três Corações, MG, Brasil",
  "to": "Santo André, SP, Brasil",
  "date": "2026-08-25",
  "seats": 1
}
```

Alterar esse arquivo na branch do coletor dispara `.github/workflows/blablacar-rendered-public-search.yml`.

## Saídas

- `collector/results/latest.json`: resultado estruturado da última execução;
- `collector/results/latest.md`: resumo legível;
- screenshot da página: artifact do GitHub Actions, não versionado no repositório.

## Segurança

- não usa cookies capturados do celular;
- não usa Bearer token pessoal;
- não usa valor `datadome` copiado do usuário;
- não implementa stealth, bypass de CAPTCHA ou evasão anti-bot;
- se a BlaBlaCar bloquear a execução, registra falha em vez de inventar dados.

## Isolamento

Este coletor não altera `MainActivity`, `LiveRideAccessibilityService`, Agenda de Viagens, FAROL, versão do APK ou release. O desenvolvimento permanece na branch `agent/blablacar-public-search-collector` até decisão explícita de integração.
