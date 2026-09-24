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
        ├── repositórios: SharedPreferences / API / arquivos
        └── NativeAudioEngine (fachada JNI)
                    ↓
             C++: TinyALSA + NAM + IR + efeitos
```

- A seleção TONE3000 usa Custom Tabs, PKCE e callback nativo; o app lista e
  baixa captures em Kotlin. `Tone3000ApiRepository` concentra troca de token,
  leitura de tones e paginação de modelos; `MainActivity` mantém o fluxo OAuth
  e a persistência dos tokens.
- `NamChainRepository` lê e grava os blocos NAM adicionais no formato JSON
  existente. O bloco principal ainda usa preferências legadas.
- `FxChainRepository` lê e grava as cadeias de IR/FX e FX nativo, preservando
  o JSON atual; a Activity continua sincronizando as entradas com o motor JNI.
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
- `ui/model/PicoloUiState.kt` mantém os modelos de apresentação e converte o
  snapshot nativo separado dos Composables.
- `ui/actions/PicoloActions.kt` descreve as ações que a UI pode executar. Os
  Composables dependem desse contrato, sem referenciar a Activity.
- `PicoloComposeViewModel` mantém estado de UI, mas recebe snapshots montados
  pela Activity através de `PicoloStateRepository`; o repositório centraliza
  a consulta periódica e o ViewModel coleta o fluxo sem polling no Composable.
  `AudioAppController` ainda concentra comandos e depende da Activity,
  portanto é uma fronteira temporária, não a camada final. Os casos de uso de
  preset e roteamento já dependem de contratos de domínio e interfaces de
  engine, mantendo a implementação JNI atrás de `NativeAudioEngine`.
- `MainActivity` ainda cuida de ciclo de vida, tela, persistência, OAuth,
  rede, arquivos e reconstrução de cadeia. As próximas extrações devem
  preservar as chaves de preferências e o comportamento de recuperação.

## Sequência de refatoração

1. **Modelagem e persistência:** extraímos o mapeamento do estado Compose, o
   armazenamento JSON dos blocos NAM adicionais e a leitura/organização dos
   presets; migrar a cadeia principal e os dados FX em mudanças separadas,
   mantendo compatibilidade com os formatos atuais.
2. **Controlador da aplicação:** presets e roteamento de áudio já usam casos de
   uso, contratos de repositório e interfaces de engine sem referências à
   Activity. Próximos fluxos: importação de tons, OAuth e downloads.
3. **Limite JNI:** extraímos as declarações e a carga da biblioteca para
   `NativeAudioEngine`, junto com a atualização coordenada dos símbolos C++.
   Os casos de uso de preset e roteamento dependem de interfaces de engine. O
   motor continua sem dependências Android e sem trabalho bloqueante no
   processamento.
4. **Estado de tela:** substituir a consulta periódica do repositório por
   publicação de snapshots imutáveis quando as ações alterarem o estado. A
   UI já observa um fluxo pelo ViewModel; hoje o fluxo ainda consulta a
   Activity a cada 700 ms porque as mutações e as chamadas JNI permanecem lá.
5. **Preferências:** migrar gradualmente para DataStore somente após cada
   repositório ter testes de compatibilidade para as chaves atuais e presets.

Cada etapa deve ser pequena o bastante para preservar o áudio ativo, os
presets salvos, imports locais e a seleção TONE3000.

## Áudio em tempo real

A cadeia de áudio permanece isolada do Android UI. Nenhuma chamada de rede,
leitura de arquivo, alocação ou chamada JNI deve entrar no callback de áudio.
TinyALSA continua como backend requerido pelo projeto; trocar o transporte por
Oboe/AAudio é uma decisão futura e independente da refatoração de camadas.
