# Update v2

**Idiomas: [English](../whats-new.md) · [Português (BR)](whats-new.md) · [简体中文](../zh-Hans/whats-new.md)**

Esta página resume o que este projeto altera em relação ao aplicativo base, tanto
para usuários finais quanto para desenvolvedores. Para firmware, veja
[build-firmware.md](build-firmware.md); para os add-ons opcionais do Klipper, veja
[mods/klipper-addons.md](mods/klipper-addons.md).

<p align="center"><img src="../images/principal-screen.png" alt="Tela principal do Kocoa Beam" width="280"></p>

## Software embutido

A stack de impressão foi atualizada para releases recentes do upstream. As
versões exatas ficam fixadas no build (`gradle.properties` e `app/build.gradle`):

| Componente | Versão embutida |
|---|---|
| Host Klipper | upstream atual (alvo do firmware do MCU: **0.13**) |
| Host Kalico | upstream atual |
| Moonraker | **0.11.0** (Web API 1.5.0) |
| Fluidd | **1.37.5** |
| Mainsail | **2.19.0** |
| Happy Hare (MMU) | **v4.0.0** |
| Moonraker-timelapse | embutido |

Os assets estáticos do Fluidd e do Mainsail são servidos com os MIME types
corretos, então os dois front ends carregam totalmente estilizados e salvar
arquivos ou configs pela interface web funciona.

<p align="center">
  <img src="../images/moonraker-version.png" alt="Página inicial do Moonraker" width="420">
  <img src="../images/fluidd-screen-klipper-version.png" alt="Página de sistema do Fluidd" width="420">
</p>

## Portas da interface web

Cada front end tem a própria porta, em vez de uma compartilhada:

| Front end | URL |
|---|---|
| Fluidd | `http://<ip-do-dispositivo>:4408/` |
| Mainsail | `http://<ip-do-dispositivo>:4409/` |

A porta acompanha o seletor de front end na tela principal, que também mostra a
URL ativa. Os endpoints de câmera continuam em `:8889`.

## Câmera / suporte a webcam USB

O servidor de câmera agora também transmite de uma webcam USB UVC genérica
(além da câmera do próprio aparelho), com troca a quente ao vivo, um
seletor que diferencia várias lentes, um controle de rotação, e uma
configuração de resolução (Baixa/Média/Alta). A transmissão também ficou
mais resiliente numa conexão fraca: um espectador lento não trava mais o
feed para todo mundo (backpressure por espectador), e a qualidade JPEG
agora se ajusta automaticamente ao que a rede consegue realmente carregar,
em vez de um tamanho fixo que simplesmente é descartado sob congestionamento.
Guia completo, incluindo como adicionar no Fluidd/Mainsail:
[`webcam.md`](webcam.md).

## Acesso remoto via OctoEverywhere

**Configurações → Acesso remoto → Ativar OctoEverywhere** executa o
companion real do [OctoEverywhere](https://octoeverywhere.com) para
Klipper, vendorizado a partir da fonte original e adaptado para rodar como
processo próprio no Android, em vez do serviço systemd/venv que ele
normalmente instala. Ele se conecta ao perfil de impressora que estiver
rodando no momento, pela mesma conexão local com o Moonraker que Fluidd e
Mainsail usam — sem configuração extra no lado do Moonraker. **Confirmado
funcionando de ponta a ponta em hardware real**, inclusive vinculando contra
os servidores reais de produção do octoeverywhere.com.

- Ativar o botão inicia o companion; **Vincular impressora** então mostra um
  QR code (assim que o companion gerar o ID da impressora, geralmente em
  poucos segundos) para concluir a vinculação da sua conta OctoEverywhere,
  o mesmo passo único de qualquer instalação. Se o "Go to Klipper" no
  octoeverywhere.com continuar dizendo que não está conectado logo após
  vincular, desative e reative o OctoEverywhere uma vez — ele só verifica o
  status de vinculação no momento em que conecta, não ao vivo.
- A telemetria de erros própria dele (Sentry) fica desativada; a conexão real
  de acesso remoto com o octoeverywhere.com não é afetada.
- Se você também ativar o servidor de câmera acima, o OctoEverywhere pode
  usar automaticamente essa mesma webcam (USB ou embutida) assim que ela for
  adicionada como câmera no Fluidd ou Mainsail — não precisa de uma
  configuração de câmera separada. A resolução/taxa de quadros padrão da
  câmera são reduzidas de propósito para isso continuar utilizável numa
  conexão remota (medido ~117KB/s a 640x480/~14fps, contra ~1,25MB/s no
  padrão original de 720p, que era lento a ponto de atrasar também a
  execução de comandos por compartilhar a mesma conexão retransmitida).
- Apenas um perfil de impressora pode ficar vinculado ao OctoEverywhere por
  vez, mesmo rodando vários perfis ao mesmo tempo.

## Acesso remoto via Obico

**Configurações → Acesso remoto → Obico** executa o companion real do
[Obico](https://www.obico.io) (`moonraker-obico`), vendorizado a partir da
fonte original e adaptado para rodar como processo próprio no Android, em
vez do serviço systemd que ele normalmente instala. Ele se conecta ao Obico
Cloud ou a um Obico Server auto-hospedado — o que estiver definido em
**Servidor Obico** — pela mesma conexão local com o Moonraker que
Fluidd/Mainsail usam, sem configuração extra do lado do Moonraker.

- Ativar o botão já inicia o companion. **Vincular impressora** mostra um
  código que o próprio companion gera (o mesmo fluxo que o script de
  instalação do Obico usa para uma impressora Klipper auto-instalada, só que
  sem precisar de terminal) — digite esse código no app/site do Obico ao
  adicionar uma impressora, ou toque em **Abrir link** para ir direto à
  página de vinculação do Obico com o código já preenchido. Um campo de
  código manual também fica disponível para o caso inverso (um código que o
  Obico te deu). **Confirmado funcionando de ponta a ponta contra os
  servidores reais do Obico Cloud**, incluindo uma impressora vinculando de
  fato pelo fluxo de código gerado.
- A telemetria de erros própria dele (Sentry) fica desativada por padrão, e
  sempre desativada para um servidor auto-hospedado independente dessa
  configuração; a conexão real com o servidor escolhido não é afetada.
- Só está disponível um snapshot periódico (~10s) da webcam, não a
  pré-visualização ao vivo via WebRTC do Obico — que precisa de um processo
  `janus-gateway` nativo e `ffmpeg`, ambos compilados para desktop
  Linux/Raspberry Pi no projeto original e não incluídos aqui. Veja
  [`obico.md`](obico.md) para detalhes.
- Apenas um perfil de impressora pode ficar vinculado ao Obico por vez,
  mesmo rodando vários perfis ao mesmo tempo.

## Visualizador de logs no app

Uma aba **Logs** expõe os logs do Klipper, do Moonraker e do aplicativo. Cada um
pode ser visto, copiado, baixado para a pasta `Downloads/` do dispositivo ou
compartilhado — sem PC nem `adb`.

<p align="center"><img src="../images/log-screen.png" alt="Aba de Logs" width="300"></p>

## Metadados e thumbnails de g-code

Antes não funcionava e foi corrigido. Trabalhos enviados agora exibem a imagem de
pré-visualização, tempo de impressão, uso de filamento e lista de objetos no
Fluidd/Mainsail. O Moonraker normalmente extrai isso iniciando um processo
auxiliar separado, o que não é possível dentro de um aplicativo Android; a
extração foi alterada para rodar in-process.

<p align="center"><img src="../images/thumbnail-metadata.png" alt="Lista de trabalhos do Fluidd com thumbnail e metadados" width="760"></p>

## Template de printer.cfg inicial

É fornecido um template de `printer.cfg` com um pacote de macros cobrindo
`PRINT_START` / `PRINT_END`, bed mesh adaptativo, calibração de pressure advance e
de velocidade, babystepping, load/unload de filamento e preheats.

## Add-ons do Klipper embutidos (opt-in)

Vendorados mas inativos até a seção correspondente ser adicionada ao
`printer.cfg`: KAMP, LED Effect, Z Calibration, Auto Speed, TMC Autotune. Veja
[mods/klipper-addons.md](mods/klipper-addons.md). Um procedimento para ajustar
input shaper sem acelerômetro está em
[mods/input-shaper-manual.md](mods/input-shaper-manual.md).

## Log de crash

Em uma saída inesperada, o aplicativo grava `last_crash.txt` para inspeção
posterior.

## Firmware do MCU

A ferramenta de build para qualquer placa suportada está documentada em
[build-firmware.md](build-firmware.md).

## Não testado

A extensão de câmera (`[beam_camera]` — controle de lanterna e autofoco) foi
mantida sem alteração e não foi testada neste projeto.
