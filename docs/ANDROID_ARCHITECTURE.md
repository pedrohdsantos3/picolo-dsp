# Arquitetura Android

Este projeto mantém o processamento de áudio em C++/TinyALSA por causa da
latência e do controle necessário sobre a interface USB. A organização Android
segue uma migração incremental para a arquitetura recomendada pelo Android,
sem colocar lógica de negócio ou operações bloqueantes no callback de áudio.

## Camadas

```text
UI (Activity/WebView + estado de tela)
        ↓ eventos e UiState
Domínio (regras da cadeia e casos de uso)
        ↓ modelos próprios da aplicação
Dados (preferências, arquivos, OAuth/browser)
        ↓ API estreita e síncrona no limite de áudio
JNI / C++ (TinyALSA + NAM + IR + DSP)
```

O Android recomenda separar UI e dados, usar estado imutável e fluxo
unidirecional; uma camada de domínio é opcional e deve ser introduzida apenas
quando evita duplicação ou concentra regras reutilizáveis.

## Estado atual da migração

- `data/model/ExtraNamEntry.kt` contém o modelo persistido da cadeia, fora da
  `MainActivity`.
- A Activity ainda é o state holder legado e concentra a ponte do frontend
  oficial, compatibilidade OAuth e chamadas JNI. Isso é deliberado nesta etapa:
  mover essas rotinas sem testes de regressão poderia interromper o áudio ao
  vivo.
- O thread de áudio continua totalmente nativo. Nenhuma chamada de rede,
  leitura de arquivo, alocação ou chamada JNI deve ser adicionada ao caminho de
  processamento por bloco.

## Próximas etapas de refatoração

1. Criar `SignalChainRepository` para encapsular SharedPreferences/JSON e
   expor `StateFlow<SignalChainState>`.
2. Criar `AudioEngine` como fachada Kotlin para a API JNI; manter os métodos
   JNI agrupados em poucos arquivos, conforme as recomendações do NDK.
3. Extrair `ToneLoadUseCase`, `PresetUseCase` e `AudioRoutingUseCase` para
   remover regras de negócio da Activity.
4. Transformar a tela legada em um state holder explícito, preservando o
   WebView oficial como componente de apresentação.
5. Migrar preferências de configuração para DataStore no repositório, com
   migração compatível e sem alterar os nomes atuais das chaves.
6. Adicionar testes unitários para serialização, reordenação, tipos AMP/IR e
   restauração de presets antes de cada extração maior.

## Áudio de baixa latência

As recomendações oficiais de Oboe/AAudio (modo low-latency, callback, 48 kHz,
buffer duplo e ausência de operações bloqueantes) devem ser usadas como
referência para uma futura camada de transporte. A implementação TinyALSA atual
é mantida porque é requisito do projeto e deve continuar isolada atrás do
backend nativo.
