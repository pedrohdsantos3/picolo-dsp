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
        repositórios: DataStore / API / arquivos
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
  `NamChainEditController` coordena mover/remover sobre a cadeia completa
  (modelo primário e blocos adicionais), delegando rebuild e persistência pelos
  callbacks que mantêm as preferências legadas compatíveis.
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
  Preferences DataStore. `ToneSessionRepositoryImpl` e
  `SelectedModuleTypeRepository` também usam esse store para sessão OAuth/PKCE
  e tipo de módulo selecionado. `AppPreferencesDataStore` importa todos os tipos
  suportados do arquivo SharedPreferences legado; os valores existentes no
  DataStore prevalecem e o arquivo antigo é mantido como backup.
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
- `DataStoreSharedPreferences` mantém compatibilidade síncrona para os
  repositórios legados enquanto persiste no DataStore. Escritas são aplicadas
  imediatamente ao cache em memória e serializadas em background; código novo
  deve preferir APIs suspensas dos repositórios.
- `controller/AudioParameterController` concentra os limites, a chamada da
  engine e a persistência dos controles globais de ganho, gate e EQ. A Activity
  fornece callbacks concretos de JNI e preferências; o restante do
  `AudioAppController` ainda coordena outras ações e será extraído em etapas.
- `controller/NamParameterController` aplica alterações por bloco NAM e usa
  callbacks para persistência, JNI e status. A gravação pontual não reconstrói
  nem reposiciona a cadeia IR/FX. Navegação e importação continuam no controller
  interno até as próximas extrações.
- `controller/FxParameterController` e `controller/CabinetParameterController`
  isolam os controles de bypass, mix, parâmetros, EQ e posição com dependências
  injetadas. O controller interno ainda mantém parte das operações de importação
  e remoção de cadeias.
- `controller/FxNativeChainController` aplica add/remove de efeitos nativos,
  persistindo e sincronizando a cadeia antes de retomar o áudio.
- `controller/FxChainRemovalController` e `controller/CabinetRemovalController`
  coordenam limpeza da cadeia nativa, persistência, arquivos e retomada do áudio
  por callbacks testáveis.
- `controller/SignalChainReorderController` aplica a ordem mista NAM/FX/IR ao
  motor e às preferências por callbacks injetados; `MainActivity` traduz o
  snapshot JSON legado para os identificadores tipados dos blocos.
- `controller/ResetToDefaultController` coordena a restauração do grafo de
  áudio, seleção do tone, EQ, bypass e preferências de módulos. A Activity
  fornece callbacks Android/JNI e de repositório; a sequência tem teste unitário.
- `controller/AudioSessionController` prepara os efeitos nativos antes do start,
  coordena start/stop e encaminha mudanças de rota. A Activity fornece apenas
  callbacks de engine, roteamento e status.
- `controller/PackageCaptureController` resolve o token e carrega em coroutine
  as captures associadas aos blocos NAM, FX e IR. A Activity resolve os
  metadados locais do bloco e continua responsável por persistir o contexto de
  importação e abrir o seletor apropriado.
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
   dependências fora da Activity. Controles globais, NAM, FX e IR já foram
   extraídos para controllers testáveis. O carregamento de captures de pacote
   também usa um controller próprio, e o movimento de blocos NAM é coordenado
   por um controller testável. Remoção de bloco NAM e add/remove da cadeia FX
   nativa e reordenação mista da cadeia também foram extraídos. Ainda há ações de
   coordenação de importação e os metadados finais da substituição NAM ainda
   ficam na borda da Activity.
3. **Limite JNI:** concluído. `NativeAudioEngine` concentra a carga da
   biblioteca e declarações JNI, e os casos de uso de preset e roteamento usam
   interfaces de engine. O callback de áudio continua sem dependências Android.
4. **Estado de tela:** snapshots tipados são publicados após ações e mudanças de
   status; só as métricas contínuas do áudio são consultadas a cada 700 ms. Os
   controles Android auxiliares e a ponte por `TextWatcher` foram removidos.
   A serialização JSON ainda existe na borda Android para ler o estado legado;
   o repositório e a ViewModel recebem apenas `PicoloUiState`.
5. **Preferências:** concluído. DataStore é a fonte ativa para cache de captures,
   OAuth/PKCE, tipo de módulo, NAM, FX/IR, presets, roteamento, modelo ativo e
   configurações de áudio. `DataStoreSharedPreferences` preserva temporariamente
   as chamadas síncronas existentes; novos contratos devem expor APIs suspensas.
   A migração preenche apenas chaves ausentes e mantém o arquivo legado como
   backup para recuperação.

Cada etapa deve ser pequena o bastante para preservar o áudio ativo, os
presets salvos, imports locais e a seleção TONE3000.

## Áudio em tempo real

A cadeia de áudio permanece isolada do Android UI. Nenhuma chamada de rede,
leitura de arquivo, alocação ou chamada JNI deve entrar no callback de áudio.
TinyALSA continua como backend requerido pelo projeto; trocar o transporte por
Oboe/AAudio é uma decisão futura e independente da refatoração de camadas.
