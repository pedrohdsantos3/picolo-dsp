# PoC Oboe USB no Galaxy S20 FE

Branch: `feat/s20fe-oboe-usb-audio-poc`.

Esta branch adiciona o pacote oficial Oboe 1.11.0 e tenta abrir a EVO4 por
Oboe antes dos backends já existentes. A tentativa usa os IDs de dispositivo
que o Android publica via `AudioManager`, primeiro em modo exclusivo e depois
compartilhado. Se Oboe não abrir, a engine registra a razão e continua pelo
TinyALSA/AAudio existente.

## Compilar e instalar

```sh
./gradlew assembleDebug
adb -s <serial-do-s20fe> install -r app/build/outputs/apk/debug/app-debug.apk
```

No app, abra o menu superior e escolha **Iniciar áudio**. Para ver o diagnóstico:

```sh
adb -s <serial-do-s20fe> logcat -s Tone3000Native:I
```

Procure por `Oboe EVO4 test path unavailable` ou `Opened EVO4 through Oboe`.
O estado `AUDIO ACTIVE · EVO4` sozinho não identifica Oboe, porque o fallback
TinyALSA também pode manter a engine ativa. O monitor do editor e a linha
`Opened EVO4 through Oboe` no log nativo identificam melhor o backend.

## Resultado anterior no S20 FE

Com a EVO4 conectada, a PoC registrou:

```text
Oboe EVO4 test path unavailable; trying existing USB backend: Android AudioManager did not expose both EVO4 device IDs (input=-1, output=-1)
Opened USB audio: EVO4 card=1 device=0 capture=4ch/S32_LE periods=4 playback=4ch/S32_LE periods=4 block=256
```

O Android USB host detecta a EVO4, mas o `AudioManager` não publica IDs de
entrada/saída para ela. Assim, Oboe não chega a abrir streams nesse aparelho;
TinyALSA abre a interface diretamente e mantém o app utilizável. Este resultado
separa a visibilidade USB do caminho de áudio Android. Não demonstra falha de
processamento Oboe nem confirma áudio audível pelo fallback.

Naquele teste, o Android não publicou IDs de entrada/saída para a EVO4, então o
novo processamento Oboe não chegou a ser exercitado nesse aparelho. A rota
TinyALSA continuou disponível.

## Processamento Oboe atualizado

Quando o Android publica os dois IDs USB, o backend tenta abrir Oboe em modo
exclusivo e depois compartilhado. A saída abre primeiro e recebe o callback de
alta prioridade; a entrada solicita capacidade de buffer duas vezes maior que
a saída. O callback lê a entrada sem bloquear e usa filas pré-alocadas para
alimentar o worker NAM de 64 frames. O processamento NAM e os efeitos ficam
fora do callback. O monitor do editor exibe XRuns de entrada/saída, orçamento
DSP, erros de I/O e clipping digital.

O funcionamento desse caminho foi verificado depois em um Samsung SM-S916B
(Galaxy S23+) com a EVO4: duas leituras em uma janela curta mostraram zero
XRuns de captura e reprodução. Esse resultado não altera o diagnóstico do
S20 FE acima, onde o Android não forneceu IDs Oboe para a interface.
