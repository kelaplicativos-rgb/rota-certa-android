# Laboratório determinístico de reprodução do farol

## Objetivo

Converter falhas observadas no relatório `rota-certa-relatorio-depuracao (29).txt` em uma sequência sanitizada e reproduzível. O laboratório é exclusivamente de teste: não importa classes Android, não altera o serviço de acessibilidade e não muda o comportamento da bolinha.

## Evidências preservadas

A fixture mantém somente identificadores técnicos e contagens necessárias:

- 1.440 rejeições de pacotes externos;
- 562 solicitações de limpeza;
- 455 eventos repetidos da mesma tela;
- seis ocorrências de falha, divididas entre pacote selecionado e pacotes externos;
- eventos descartados pelos gravadores atual e recuperado;
- troca de janela e geração;
- resultado de rota atrasado;
- raiz nula e raiz pertencente a outro pacote;
- desaparecimento do card.

Endereços e textos pessoais foram substituídos por aliases determinísticos.

## Invariantes executáveis

O replay falha quando qualquer uma destas regras é violada:

1. pacote externo não pode consultar ou autorizar a raiz antiga do aplicativo monitorado;
2. geração anterior não pode aplicar rota nem repintar o farol;
3. verde ou vermelho exigem destino confirmado e distância real;
4. cinza ou amarelo não podem conservar quilômetros;
5. falha contida termina sem destino e sem distância;
6. eventos equivalentes são confluídos;
7. limpeza repetida é idempotente;
8. desaparecimento do card elimina imediatamente a decisão atual.

## Execução

```bash
python3 -m unittest discover -s tests -p 'test_farol_trace_lab.py' -v
python3 tools/farol_trace_lab.py \
  --fixture tests/fixtures/farol_trace_20260806_sanitized.json \
  --strict
```

## Limite desta etapa

O laboratório define a reprodução e o oráculo esperado. A ligação dos mesmos envelopes e invariantes ao código Kotlin de produção pertence à próxima etapa e só deve começar após autorização explícita.
