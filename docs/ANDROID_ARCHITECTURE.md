# Arquitetura Android

O app usa Jetpack Compose para a interface e Kotlin para os fluxos de aplicação.
O processamento em tempo real continua em C++/TinyALSA; chamadas de rede,
I/O de arquivos e trabalho de UI ficam fora do callback de áudio.

## Limites atuais

```text
Jetpack Compose + PicoloComposeViewModel
        ↓ estado e ações
PicoloActions (contrato tipado)
        ↓
        AudioAppController (ainda hospedado em MainActivity)
        ├── PicoloAppContainer (monta repositórios e casos de uso)
        ├── contratos de domínio
        └── NativeAudioEngine (fachada JNI)
              ↓
        repositórios: DataStore / SharedPreferences / API / arquivos
                    ↓
             C++: TinyALSA + NAM + IR + efeitos
```

- A seleção TONE3000 usa Custom Tabs, PKCE e callback nativo. A Activity mantém
  a navegação do browser e a leitura da URI; `PrepareToneAuthorizationUseCase`
  gera o desafio PKCE e `CompleteToneSelectionUseCase` valida o estado, troca
  o código, persiste tokens pela sessão e carrega tone/captures.
  `ToneSessionRepositoryImpl` salva tokens e PKCE no Preferences DataStore.
  `Tone3000ApiRepository`
  implementa a API e o download autenticado; `ToneImportRepositoryImpl` cuida
  dos arquivos locais, normalização WAV e commit dos modelos. Casos de uso
  deixam essas operações atrás de contratos de domínio.
- `NamChainRepository` lê e grava os blocos NAM adicionais no formato JSON
  existente usando `ExtraNamEntry` do domínio. `AddExtraNamCaptureUseCase`
  coordena o commit, a inclusão no motor, os defaults AMP/PEDAL e a persistência
  de um bloco novo. `RebuildNamChainUseCase` restaura os blocos e controles pela
  interface `NamChainRebuildEngine`; `ReplaceNamCaptureUseCase` valida e aplica
  uma substituição antes do commit dos metadados da cadeia.
  `LoadPrimaryToneCaptureUseCase` só confirma a troca depois que a
  interface `PrimaryToneCaptureEngine` aceita o arquivo e aplica os defaults do
  módulo. `PrepareNamBlockReplacementUseCase` mantém controles do bloco ao
  trocar capture e redefine os valores ao mudar entre AMP e PEDAL. A Activity
  ainda persiste os metadados finais do replace.
- `FxChainRepository` mapeia os JSONs legados de FX para modelos Kotlin
  imutáveis (`FxImpulseEntry` e `FxNativeEntry`) e vice-versa. A Activity
  converte esses modelos para JSON somente ao montar o snapshot de apresentação.
  `ImportFxCaptureUseCase` coordena download, normalização, carga do slot,
  persistência e aplicação dos controles iniciais no motor.
- `PresetRepository` define o contrato de domínio; `PresetRepositoryImpl`
  concentra a leitura, gravação, ativação e organização dos slots usando as
  chaves legadas e os arquivos atuais. `SavePresetUseCase` coordena a gravação
  e `LoadPresetUseCase` troca o modelo no motor antes de ativar suas
  preferências. `PresetData` é o modelo imutável compartilhado entre domínio e
  apresentação.
- `AudioRoutingUseCase` coordena o contrato `AudioRoutingRepository` e sua
  implementação persiste as rotas selecionadas e as restaura no motor. A
  Activity ainda atualiza os elementos visuais após as ações de roteamento.
- `NativeAudioEngine` concentra a carga da biblioteca e as declarações JNI;
  os nomes dos exports C++ acompanham essa classe. A Activity usa a fachada,
  mas ainda coordena parte dos comandos através do controller interno.
- `CurrentToneRepositoryImpl` lê o caminho ativo, salva o modelo e aplica defaults NAM pelas chaves legadas. `RestorePreviousToneModelUseCase` valida o arquivo e pede à interface `ToneModelEngine` para restaurá-lo após uma troca malsucedida. `NativeAudioEngine` implementa essa interface sem expor JNI ao caso de uso.
- `TonePackageCaptureRepositoryImpl` mantém o cache de captures de pacote no
  Preferences DataStore. `AppPreferencesDataStore` migra apenas as chaves desse
  cache e da sessão OAuth, deixando as outras preferências SharedPreferences
  intactas.
  `LoadPackageCapturesUseCase` escolhe a arquitetura da API, combina resultados
  novos e em cache e atualiza o cache sem descartar captures omitidos por
  respostas parciais.
- `ImportCabinetImpulseUseCase` coordena o download, normalização WAV, carga no motor e início do áudio. `CabinetImpulseRepositoryImpl` mantém as chaves e defaults legados de cabinet IR.
- `ui/model/PicoloUiState.kt` mantém os modelos de apresentação e converte o
  snapshot legado para `PicoloUiState` na borda Android, separado dos Composables.
- `ui/actions/PicoloActions.kt` descreve as ações que a UI pode executar. Os
  Composables dependem desse contrato, sem referenciar a Activity.
- `PicoloComposeViewModel` mantém estado de UI e coleta snapshots tipados
  publicados após comandos da UI e alterações no status. `ui/state/PicoloStateRepository`
  consulta periodicamente apenas as métricas contínuas de áudio necessárias aos
  indicadores de desempenho. JSON não atravessa mais o repositório nem a
  ViewModel; o status é parte de `PicoloUiState`. Não há `TextView`, `Button` ou
  `SeekBar` auxiliares sem conexão com a tela.
- `PicoloAppContainer` monta o motor, os repositórios e os casos de uso sem
  depender da Activity. `AudioAppController` ainda está declarado dentro dela e
  usa helpers de navegação e importação da Activity.
- `controller/AudioParameterController` concentra os limites, a chamada da
  engine e a persistência dos controles globais de ganho, gate e EQ. A Activity
  fornece callbacks concretos de JNI e SharedPreferences; o restante do
  `AudioAppController` ainda coordena outras ações e será extraído em etapas.
- `MainActivity` cuida do ciclo de vida Android, permissões, seletores e
  navegação OAuth; Compose é a única interface. OAuth, API, armazenamento dos
  presets, cache de captures, restauração do modelo ativo e importação NAM/FX/IR
  passam por limites próprios. Parte da coordenação de ações ainda pertence ao
  controller hospedado na Activity.

## Sequência de refatoração

1. **Modelagem e persistência:** modelos Kotlin e repositórios já cobrem o estado
   Compose, cadeias NAM/FX/IR, presets, modelo ativo e captures. Os formatos e
   chaves legados permanecem compatíveis e têm cobertura instrumentada.
2. **Controlador da aplicação:** casos de uso coordenam presets, roteamento,
   OAuth/API, captures, restauração e importações. `PicoloAppContainer` monta as
   dependências fora da Activity. Os controles globais de áudio já foram
   extraídos para um controller testável. Falta mover o restante de
   `AudioAppController` e a coordenação de persistência para componentes sem
   referência à Activity; os metadados finais da substituição NAM ainda são
   gravados nela.
3. **Limite JNI:** concluído. `NativeAudioEngine` concentra a carga da
   biblioteca e declarações JNI, e os casos de uso de preset e roteamento usam
   interfaces de engine. O callback de áudio continua sem dependências Android.
4. **Estado de tela:** snapshots tipados são publicados após ações e mudanças de
   status; só as métricas contínuas do áudio são consultadas a cada 700 ms. Os
   controles Android auxiliares e a ponte por `TextWatcher` foram removidos.
   A serialização JSON ainda existe na borda Android para ler o estado legado;
   o repositório e a ViewModel recebem apenas `PicoloUiState`.
5. **Preferências:** DataStore migrou cache de captures e sessão OAuth. NAM,
   FX/IR, presets, roteamento, modelo ativo e configurações de áudio ainda usam
   SharedPreferences; a migração desses grupos requer adaptar os contratos
   síncronos e cobrir a compatibilidade de cada grupo.

Cada etapa deve ser pequena o bastante para preservar o áudio ativo, os
presets salvos, imports locais e a seleção TONE3000.

## Áudio em tempo real

A cadeia de áudio permanece isolada do Android UI. Nenhuma chamada de rede,
leitura de arquivo, alocação ou chamada JNI deve entrar no callback de áudio.
TinyALSA continua como backend requerido pelo projeto; trocar o transporte por
Oboe/AAudio é uma decisão futura e independente da refatoração de camadas.
