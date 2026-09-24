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
        AudioAppController (adaptador ainda hospedado em MainActivity)
        ├── casos de uso e contratos de domínio
        ├── repositórios: DataStore / SharedPreferences / API / arquivos
        └── NativeAudioEngine (fachada JNI)
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
  snapshot nativo separado dos Composables.
- `ui/actions/PicoloActions.kt` descreve as ações que a UI pode executar. Os
  Composables dependem desse contrato, sem referenciar a Activity.
- `PicoloComposeViewModel` mantém estado de UI e coleta snapshots publicados
  após comandos da UI e alterações no status. `PicoloStateRepository` consulta
  periodicamente apenas as métricas contínuas de áudio necessárias aos
  indicadores de desempenho. `AudioAppController` ainda depende da Activity.
- `MainActivity` cuida do ciclo de vida Android e é a única dona da tela Compose;
  a árvore de Views antiga foi removida. OAuth, API, armazenamento dos presets,
  cache de captures, restauração do modelo ativo e importação NAM/FX/IR já passam
  por limites próprios. Alguns adaptadores de status e controles ainda ficam na
  Activity.

## Sequência de refatoração

1. **Modelagem e persistência:** estado Compose, blocos NAM adicionais,
   presets, dados de FX/IR, modelo ativo e cache de captures usam modelos Kotlin
   nas fronteiras internas. Os formatos JSON e as chaves antigas continuam
   compatíveis e têm cobertura instrumentada. A migração para DataStore fica
   pendente enquanto a Activity ainda lê e grava preferências diretamente.
2. **Controlador da aplicação:** presets, roteamento, OAuth/API, carga de
  captures, validação do modelo principal, restauração e importação de FX/IR já
  usam casos de uso e contratos. Os fluxos add/replace NAM e FX/IR já usam casos
  de uso; a Activity ainda persiste os metadados do replace e atualiza os
  componentes visuais após o resultado.
3. **Limite JNI:** extraímos as declarações e a carga da biblioteca para
   `NativeAudioEngine`, junto com a atualização coordenada dos símbolos C++.
   Os casos de uso de preset e roteamento dependem de interfaces de engine. O
   motor continua sem dependências Android e sem trabalho bloqueante no
   processamento.
4. **Estado de tela:** ações Compose e alterações no status publicam snapshots
   imutáveis. A consulta periódica de 700 ms foi reduzida às métricas contínuas
   do áudio necessárias aos indicadores de desempenho.
5. **Preferências:** migração gradual para DataStore iniciada pelo cache de
   captures e pela sessão OAuth, com cobertura instrumentada dos formatos
   legados. As demais preferências ainda usam SharedPreferences e devem migrar
   após extração e cobertura de compatibilidade dos repositórios
   correspondentes.

Cada etapa deve ser pequena o bastante para preservar o áudio ativo, os
presets salvos, imports locais e a seleção TONE3000.

## Áudio em tempo real

A cadeia de áudio permanece isolada do Android UI. Nenhuma chamada de rede,
leitura de arquivo, alocação ou chamada JNI deve entrar no callback de áudio.
TinyALSA continua como backend requerido pelo projeto; trocar o transporte por
Oboe/AAudio é uma decisão futura e independente da refatoração de camadas.
