# Arquitetura Android

Este projeto mantém o processamento de áudio em C++/TinyALSA por causa da
latência e do controle necessário sobre a interface USB. A organização Android
segue uma migração incremental para a arquitetura recomendada pelo Android,
sem colocar lógica de negócio ou operações bloqueantes no callback de áudio.

## Camadas

```text
UI (Jetpack Compose + ViewModel/StateFlow)
        ↓ eventos e UiState
Controle Kotlin existente (PluginBridge)
        ↓ modelos próprios da aplicação
Dados e integração (preferências, arquivos, OAuth/browser)
        ↓ API estreita e síncrona no limite de áudio
JNI / C++ (TinyALSA + NAM + IR + DSP)
```

O Android recomenda separar UI e dados, usar estado imutável e fluxo
unidirecional; uma camada de domínio é opcional e deve ser introduzida apenas
quando evita duplicação ou concentra regras reutilizáveis.

## Estado atual da migração

- `data/model/ExtraNamEntry.kt` contém o modelo persistido da cadeia, fora da
  `MainActivity`.
- Jetpack Compose é a UI principal do app. `PicoloComposeViewModel` mantém um
  `StateFlow` de tela e recebe snapshots da Activity; `PluginBridge` continua
  concentrando ações de UI e comandos já existentes.
- O WebView oficial fica restrito ao fluxo autenticado de seleção/importação
  do TONE3000 e pode ser fechado para retornar ao Compose. As rotinas OAuth e
  de download continuam na Activity nesta etapa.
- A Activity ainda concentra preferências, arquivos e algumas rotinas de
  negócio. A extração para repositórios/controlador Kotlin deve ser feita com
  testes de regressão para não interromper o áudio ao vivo.
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
4. Substituir a atualização periódica de snapshots da UI por estado emitido
   pelo repositório/controlador, mantendo operações pesadas fora da UI.
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
