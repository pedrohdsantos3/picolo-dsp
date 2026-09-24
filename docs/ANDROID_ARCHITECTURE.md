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
        ↓
SharedPreferences / arquivos / OAuth / downloads
        ↓ API JNI
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
- `ui/model/PicoloUiState.kt` mantém os modelos de apresentação e converte o
  snapshot nativo separado dos Composables.
- `ui/actions/PicoloActions.kt` descreve as ações que a UI pode executar. Os
  Composables dependem desse contrato, sem referenciar a Activity.
- `PicoloComposeViewModel` mantém estado de UI, mas recebe snapshots montados
  pela Activity através de `PicoloStateRepository`; o repositório centraliza
  a consulta periódica e o ViewModel coleta o fluxo sem polling no Composable.
  `AudioAppController` ainda concentra comandos e depende da Activity,
  portanto é uma fronteira temporária, não a camada final.
- `MainActivity` ainda cuida de ciclo de vida, tela, persistência, OAuth,
  rede, arquivos e reconstrução de cadeia. As próximas extrações devem
  preservar as chaves de preferências e o comportamento de recuperação.

## Sequência de refatoração

1. **Modelagem e persistência:** extraímos o mapeamento do estado Compose e o
   armazenamento JSON dos blocos NAM adicionais; migrar a cadeia principal e
   os dados FX em mudanças separadas, mantendo compatibilidade com presets.
2. **Controlador da aplicação:** mover comandos e fluxos de
   `AudioAppController` para casos de uso/repositórios independentes da
   Activity. Começar por presets e roteamento de áudio, depois importação de
   tons, OAuth e downloads.
3. **Limite JNI:** agrupar chamadas JNI atrás de uma fachada `AudioEngine`
   e retirar declarações nativas de `MainActivity`. Manter o motor de áudio
   sem dependências Android e sem trabalho bloqueante no processamento.
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
