# Rota Certa 0.1.171 — toque longo configurável

- Cada módulo poderá oferecer a opção **Ação ao manter pressionado o atalho**.
- Opções mínimas: não fazer nada, abrir o módulo, executar a ação rápida principal e executar uma ação secundária válida daquele módulo.
- Comportamentos longos já existentes devem ser preservados como padrão de migração.
- Ações destrutivas ou sensíveis devem exigir confirmação.
- Toque simples permanece determinístico e independente da personalização do toque longo.
- Configuração local, sem polling, serviço adicional ou trabalho em segundo plano.
- Falha ou ação indisponível deve encerrar de forma segura e manter o módulo acessível pela Home.
