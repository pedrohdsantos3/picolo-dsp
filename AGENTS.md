# Regras de desenvolvimento

## Direção do projeto

- O aplicativo Android usa Kotlin e Jetpack Compose para toda a interface.
- Não reintroduza React, Vite, JavaScript/TypeScript, WebView ou uma ponte
  JavaScript. Não adicione uma segunda interface web ao APK.
- Preserve a interface, os fluxos de áudio e a integração TONE3000 existentes
  ao fazer refatorações incrementais.

## Arquitetura

- Mantenha `MainActivity` como ponto de integração Android: ciclo de vida,
  permissões, navegação e adaptação dos serviços Android. Não concentre nela
  novas regras de negócio, acesso à rede ou persistência.
- Composables recebem contratos tipados de ações e estado de apresentação.
  Não os conecte diretamente à Activity, `SharedPreferences` ou JNI.
- Use ViewModels para estado de tela observável e repositórios para acesso a
  dados. Prefira modelos Kotlin imutáveis em vez de transportar JSON entre
  camadas.
- Coloque novos modelos persistidos em `data/model`, acesso a dados em
  `data/repository` e contratos de UI em `ui/actions` e `ui/model`.
- Extraia casos de uso quando um fluxo combinar várias operações ou regras;
  mantenha cada extração pequena, com dependências explícitas e sem referências
  à Activity.
- Faça dependências de plataforma e engine passarem por interfaces/fachadas.
  A Activity não deve ser o contrato de domínio para a engine JNI.

## Persistência e compatibilidade

- Preserve os nomes atuais das chaves de `SharedPreferences` e os formatos de
  presets, cadeias NAM e efeitos ao alterar armazenamento.
- Migrações precisam continuar lendo dados já salvos e não podem descartar
  preferências ou capturas locais.
- Mantenha validação de caminho, existência de arquivos e prevenção de entradas
  duplicadas nos repositórios que manipulam modelos locais.
- DataStore só deve substituir preferências depois que a camada de persistência
  mantiver compatibilidade com as chaves legadas.

## Áudio nativo e JNI

- Preserve TinyALSA, NAM, IR e os efeitos existentes, incluindo alterações
  locais em C++.
- O callback de áudio deve permanecer em tempo real: não faça nele chamadas de
  rede, I/O de arquivos, alocações evitáveis, acesso Android ou trabalho de UI.
- Não mova processamento de áudio para Kotlin. Mudanças em JNI precisam manter
  os contratos e os símbolos usados pelo C++ até que uma migração coordenada
  altere os dois lados.
- Leituras de estado e operações de controle ficam fora do callback de áudio.

## Rede e fluxos TONE3000

- Preserve OAuth com PKCE, `state`, URI de callback, paginação de modelos,
  timeouts e mensagens úteis de erro.
- Faça chamadas de rede e operações de arquivo fora da thread principal.
- Não exponha tokens, verifier PKCE ou conteúdo privado em logs.
- A seleção ou falha de importação não deve apagar o modelo ativo antes que o
  novo modelo seja baixado e carregado com sucesso.

## Mudanças e revisão

- Faça refatorações em etapas pequenas e mantenha cada etapa compilável.
- Preserve alterações preexistentes do workspace; não reverta arquivos ou
  mudanças de outras tarefas como parte de uma refatoração sem relação.
- Atualize `docs/ANDROID_ARCHITECTURE.md` quando os limites entre camadas
  mudarem.
- Ao concluir uma mudança, informe os arquivos e comportamentos afetados e
  quais verificações foram executadas ou ficaram pendentes.
