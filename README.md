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

## Build

Clone os submódulos e compile com o JDK do Android Studio:

```bash
git submodule update --init --recursive
./gradlew :app:assembleDebug
```
