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

## Suporte a webcam USB

O servidor de câmera (**Configurações → Câmera → Ativar servidor de câmera**)
agora também transmite de uma webcam USB UVC genérica, além da câmera do
próprio aparelho:

- Com **Fonte da câmera** em **Automático**, uma webcam USB conectada tem
  prioridade sobre a câmera embutida, e conectar/desconectar a webcam a
  quente é detectado em tempo real.
- **Configurações → Câmera → Fonte da câmera** permite fixar uma câmera
  específica (frontal/traseira embutida, ou uma webcam USB específica) em vez
  de depender da detecção automática.

Isso depende do aparelho expor a webcam USB pela API Camera2 padrão do
Android como câmera externa (`LENS_FACING_EXTERNAL`), suportada pela maioria
dos aparelhos baseados em AOSP desde o Android 9, mas que algumas camadas de
câmera de fabricantes podem não expor. Não foi testado em hardware real com
uma webcam UVC neste projeto.

## Acesso remoto via OctoEverywhere

**Configurações → Acesso remoto → Ativar OctoEverywhere** executa o
companion real do [OctoEverywhere](https://octoeverywhere.com) para
Klipper, vendorizado a partir da fonte original e adaptado para rodar como
processo próprio no Android, em vez do serviço systemd/venv que ele
normalmente instala. Ele se conecta ao perfil de impressora que estiver
rodando no momento, pela mesma conexão local com o Moonraker que Fluidd e
Mainsail usam — sem configuração extra no lado do Moonraker.

- Ativar o botão inicia o companion; **Vincular impressora** então mostra um
  QR code (assim que o companion gerar o ID da impressora, geralmente em
  poucos segundos) para concluir a vinculação da sua conta OctoEverywhere,
  o mesmo passo único de qualquer instalação.
- A telemetria de erros própria dele (Sentry) fica desativada; a conexão real
  de acesso remoto com o octoeverywhere.com não é afetada.
- Se você também ativar o servidor de câmera acima, o OctoEverywhere pode
  usar automaticamente essa mesma webcam (USB ou embutida) assim que ela for
  adicionada como câmera no Fluidd ou Mainsail — não precisa de uma
  configuração de câmera separada.
- Apenas um perfil de impressora pode ficar vinculado ao OctoEverywhere por
  vez, mesmo rodando vários perfis ao mesmo tempo.

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
