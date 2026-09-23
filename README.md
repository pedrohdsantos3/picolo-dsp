# Tone3000M1

Aplicativo Android experimental para processamento NAM em tempo real usando uma
interface de áudio USB. O backend atual acessa a interface diretamente com
TinyALSA.

## Importante antes de publicar

> **LEMBRETE DE DOCUMENTAÇÃO:** antes de distribuir o aplicativo, documentar e
> testar cuidadosamente todos os passos necessários para usar o backend
> TinyALSA. O público principal inclui músicos sem experiência com Android,
> ADB, root ou linha de comando; as instruções precisam ser guiadas, visuais e
> difíceis de executar incorretamente.

A documentação final deverá incluir, no mínimo:

- lista de aparelhos e versões de Android testados;
- interfaces USB testadas, cabos/adaptadores OTG e requisitos de alimentação;
- como preparar o aparelho e conceder as permissões necessárias;
- como configurar o roteamento de áudio USB exigido pelo TinyALSA;
- como instalar e ativar, de forma persistente, o componente privilegiado que
  aplica `SCHED_FIFO` à thread `Tone3000Audio`;
- verificação automática de TinyALSA, interface USB, canais, sample rate e
  `SCHED_FIFO` antes de liberar o botão de iniciar;
- mensagens claras de correção para cada falha, sem exigir leitura do Logcat;
- procedimento de recuperação e desinstalação que restaure as configurações do
  aparelho;
- um instalador guiado ou preparação de aparelho em uma única etapa, evitando
  que o usuário precise digitar comandos manualmente;
- aviso explícito de que o modo ao vivo só deve ser usado depois que o teste de
  estabilidade e xruns for aprovado.

Não considerar o fluxo pronto para usuários finais enquanto a preparação ainda
depender de instruções informais ou comandos não validados.

## Componente root experimental

O script [`root-service/tone3000-root-service.sh`](root-service/tone3000-root-service.sh)
é um protótipo do componente privilegiado persistente. Ele:

- identifica a EVO4 em `/proc/asound/cards` e libera somente seus nós PCM e de
  controle;
- desativa o roteamento USB automático do Android, evitando que o AudioFlinger
  dispute a interface com o TinyALSA;
- detecta o processo `com.pedro.tone3000m1` e aplica `SCHED_FIFO:2` somente à
  thread `Tone3000Audio`;
- reaplica a configuração quando o app reinicia ou a interface USB reconecta;
- oferece `once`, `status` e `stop` para instalação e diagnóstico.

O script deve ser executado por um gerenciador root como serviço de boot. O
módulo Magisk abaixo automatiza essa inicialização, mas ainda não é um fluxo
guiado adequado para usuários finais.

Alguns firmwares Samsung também bloqueiam o acesso por SELinux. O modo
permissivo pode ser habilitado explicitamente com
`TONE3000_SELINUX_PERMISSIVE=1`, mas isso reduz a segurança de todo o aparelho e
serve somente para desenvolvimento. A versão distribuível deverá usar uma
política SELinux mínima e específica, sem colocar o sistema inteiro em modo
permissivo.

### Módulo Magisk

Gere o pacote instalável com:

```bash
./root-service/build-magisk-module.sh
```

O arquivo `Tone3000-Root-Companion-v0.1.0.zip` pode ser instalado pelo Magisk.
Após reiniciar, o `service.sh` mantém o companion ativo. A desinstalação encerra
o serviço e restaura o roteamento USB automático do Android.

ADB root de builds `userdebug` não equivale a Magisk: ele permite executar o
serviço durante a sessão, mas não oferece necessariamente um mecanismo de boot
persistente. Nesses aparelhos é preciso instalar Magisk ou integrar uma unidade
`init` assinada ao firmware.

Para firmwares `userdebug` que aceitam `adb remount`, a integração experimental
ao `init` pode ser instalada com:

```bash
./root-service/install-adb-userdebug.sh SERIAL_ADB
```

Esse método altera a imagem/overlay de `/system`, ativa SELinux permissivo e
exige reiniciar o Android. Remova com
`./root-service/uninstall-adb-userdebug.sh SERIAL_ADB` antes de atualizar ou
restaurar o firmware.

## Build

### Frontend oficial adaptado

A pasta `ui/` contém a árvore React/TypeScript do frontend oficial do
TONE3000, mantida sob MIT (`ui/TONE3000-PLUGIN-LICENSE`). O bridge JUCE foi
substituído por `ui/src/backend/AndroidBackend.ts`, que traduz o estado e os
controles da cadeia para `Tone3000Android`; o backend de áudio continua sendo o
TinyALSA/NAM deste aplicativo. A UI legada permanece como fallback enquanto a
adaptação dos recursos JUCE sem equivalente Android (OAuth, MIDI e stereo) é
concluída.

Para validar o bundle sem alterar o APK:

```bash
cd ui
npm install --no-audit --no-fund
npm run build
```

As dependências e o código importados continuam sujeitos às licenças próprias;
o aviso MIT do frontend oficial deve acompanhar qualquer distribuição.

## Arquitetura da cadeia de sinal

O estado exposto pela interface contém `signalChain`, uma lista ordenada de
blocos tipados (`NAM` e `CABINET_IR`). A posição do Cabinet é persistida e pode
ser alterada antes, entre ou depois dos NAMs. Presets salvam a cadeia extra, o
arquivo do Cabinet e os controles de cada bloco.

Cada NAM possui In Gain, Mix, Out Gain, normalização, A2 Lite/Full, bypass e EQ
paramétrico de seis bandas com seleção PRE/POST. O Cabinet possui os mesmos
controles de ganho/mix, bypass, EQ de seis bandas PRE/POST, remoção e posição na
cadeia. O processamento nativo aplica exatamente a posição selecionada; não
há uma convolução global adicional quando o Cabinet está em outro ponto.

#### Pedal, amp e cabinet

O desenho alvo da cadeia separa explicitamente os papéis: haverá um único bloco
`AMP`, que acessará o browser e carregará um NAM; blocos `IR` continuarão
acessando o browser, mas aceitarão somente cabinets/impulse responses; e blocos
`PEDAL` serão processadores DSP nativos, sem executar NAM. Enquanto o backend de
pedais não estiver pronto, captures classificados como `PEDAL` permanecem em
modo de compatibilidade e podem usar o caminho NAM existente.

### Roadmap de DSP para pedais

O roadmap abaixo é intencionalmente separado da implementação atual da cadeia:

1. **Contrato da cadeia:** limitar AMP a um único modelo NAM e manter IR como
   bloco independente, com browser filtrado por tipo.
2. **Base nativa:** criar uma interface C++ de processador de pedal, com
   `prepare`, processamento por bloco, bypass, mix e parâmetros atômicos,
   integrada ao mesmo thread de áudio TinyALSA.
3. **Primeiro efeito:** portar um overdrive baseado no ecossistema ChowDSP,
   começando preferencialmente pelo `chowdsp_wdf` (BSD-3-Clause) e não pelo
   plugin BYOD completo. O WDF é uma biblioteca C++ header-only de modelagem
   de circuitos em tempo real: <https://github.com/Chowdhury-DSP/chowdsp_wdf>.
4. **Controles do pedal:** expor Drive, Tone e Level no card PEDAL, com
   automação segura para o thread de áudio e presets persistentes.
5. **Validação:** comparar resposta e CPU contra um capture NAM de referência,
   medir xruns/latência no SM-G781B + EVO4 e só então substituir o fallback NAM
   para PEDAL.
6. **Expansão:** adicionar boost, distortion/fuzz, compressor, wah e modulação
   usando processadores nativos; avaliar cada licença do `chowdsp_utils` antes
   de incorporar código, pois os módulos têm licenças diferentes.

O BYOD é uma referência importante de arquitetura — cadeia configurável e
diversos circuitos de distorção — mas seu código é GPLv3. Não será incorporado
diretamente sem uma decisão explícita de licenciamento:
<https://github.com/Chowdhury-DSP/BYOD>.

Antes de aceitar uso ao vivo, validar com a interface USB conectada: ausência
de captura não é considerada falha de inicialização, mas xruns, artefatos e o
tempo de processamento devem ser medidos com o hardware real e com a cadeia
pretendida.

Para até dois NAMs, o perfil TinyALSA prioriza 128 frames a 48 kHz (prazo DSP
nominal de 2,67 ms) e cai automaticamente para 256 frames se a interface não
aceitar o período menor. A latência efetiva deve ser confirmada no hardware,
pois o firmware e o driver podem impor períodos maiores.

### Medição de referência

No Samsung SM-G781B usado no desenvolvimento, com uma Audient EVO4 conectada,
dois NAMs e o companion root ativo, a medição observada foi: bloco de 128
frames, processamento médio de 1,126 ms, máximo de 2,123 ms, orçamento de
2,667 ms, zero blocos acima do orçamento e zero erros de captura/playback. A
thread recebeu `SCHED_FIFO:2` após o serviço Magisk reaplicá-lo.

Clone os submódulos e compile com o JDK do Android Studio:

```bash
git submodule update --init --recursive
./gradlew :app:assembleDebug
```
