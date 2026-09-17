# Usando o Obico

**Idiomas: [English](../obico.md) · [Português (BR)](obico.md) · [简体中文](../zh-Hans/obico.md)**

O [Obico](https://www.obico.io) é uma plataforma de impressão 3D "smart",
open-source e feita pela comunidade: monitoramento remoto, detecção de falha
de impressão e controle remoto, a partir do Obico Cloud ou do seu próprio
Obico Server auto-hospedado. O Kocoa Beam roda o companion real do
`moonraker-obico` (vendorizado a partir de
[TheSpaghettiDetective/moonraker-obico](https://github.com/TheSpaghettiDetective/moonraker-obico)),
adaptado para rodar direto no aparelho em vez do serviço systemd que ele
normalmente instala.

## Ativando

1. Inicie um perfil de impressora (ele precisa estar **rodando** — o Obico
   se conecta ao perfil que estiver ativo no momento).
2. Vá em **Configurações → Acesso remoto → Obico** e ative **Enable Obico**.
3. Toque em **Servidor Obico** para escolher onde ele conecta:
   - **Obico Cloud** (`app.obico.io`) — o padrão, sem precisar hospedar
     nada.
   - **Auto-hospedado** — digite a URL do seu próprio
     [Obico Server](https://www.obico.io/docs/server-guides/).
   - Trocar de servidor limpa qualquer vinculação existente, já que ela só
     vale para o servidor que a emitiu.
4. Toque em **Vincular impressora**. No app ou site do Obico, adicione uma
   impressora manualmente para obter um código de verificação de 6 dígitos,
   depois digite esse código na caixa de diálogo e toque em **Vincular**.
   - Esse é um caminho de vinculação diferente (mas equivalente) do que o
     script de instalação do próprio Obico oferece: ele ou espera você tocar
     em "Link Now" num celular na mesma rede local, ou cai de volta no mesmo
     código de 6 dígitos. O Kocoa Beam sempre usa o código, já que não há
     terminal para rodar a ferramenta interativa.
5. Depois de vinculada, sua impressora aparece no app/site do Obico. Para
   desvincular, abra **Vincular impressora** de novo — a caixa de diálogo
   mostra uma opção **Desvincular** quando já está vinculada.

## Webcam

O Obico obtém um snapshot da webcam do mesmo jeito que o Fluidd/Mainsail:
pela própria lista de webcams do Moonraker. Ative o servidor de câmera e
adicione no Fluidd ou Mainsail uma vez (a config é compartilhada entre eles)
e o Obico pega automaticamente — guia completo com prints:
[`webcam.md`](webcam.md).

A pré-visualização ao vivo via WebRTC do Obico (que usa um processo
`janus-gateway` e `ffmpeg` embutidos) **não** está disponível aqui — os dois
são binários nativos compilados para desktop Linux/Raspberry Pi no projeto
original, não para Android, e não são incluídos. Essa é uma flag de
configuração documentada e suportada (`disable_video_streaming`), não um
patch no código do companion. O Obico ainda recebe um snapshot novo
periodicamente (a cada ~10s) para monitoramento e detecção de falha,
independente dessa flag.

## Observações e limitações

- Só **uma** impressora pode ficar vinculada por vez, mesmo rodando vários
  perfis ao mesmo tempo — o Obico segue o perfil que chegou a "rodando"
  primeiro.
- A telemetria de erros própria do Obico (Sentry) fica desativada por
  padrão; a conexão real com o servidor escolhido não é afetada. Ela também
  é desativada automaticamente pelo próprio companion para qualquer servidor
  auto-hospedado, independente dessa configuração.
- Isso ainda não foi testado em hardware de ponta a ponta com uma conta de
  verdade — o companion inicia e se conecta ao Moonraker corretamente, e a
  chamada de API de vinculação foi verificada contra os servidores reais do
  Obico Cloud (um código propositalmente errado retorna "inválido"
  corretamente), mas avise se encontrar algum problema ao vincular ou usar
  com uma conta de verdade.

## Solução de problemas

- **Logs**: o `obico.log` fica junto com `klippy.log`/`moonraker.log`/
  `octoeverywhere.log` na pasta `logs` daquele perfil — acesse com um app
  gerenciador de arquivos através da entrada de armazenamento do próprio
  Kocoa Beam (app Arquivos do Android → "Kocoa Beam").
- **"Não vinculado" nunca muda depois de digitar um código**: confira se o
  código não expirou (os códigos do Obico duram pouco) e se você escolheu o
  servidor certo (Cloud ou auto-hospedado) — um código de um não funciona no
  outro.
- **Servidor auto-hospedado inacessível**: a mensagem de erro mostrada na
  caixa de vinculação é o erro de rede real (ex: "não foi possível resolver
  o host") — confira a URL primeiro, incluindo `http://` vs `https://`.
