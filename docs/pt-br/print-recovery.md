# Recuperação de impressão — retomar após queda de energia

**Idiomas: [English](../print-recovery.md) · [Português (BR)](print-recovery.md) · [简体中文](../zh-Hans/print-recovery.md)**

Se a impressora perder energia, o cabo USB soltar ou o Klipper desligar no meio
de uma impressão, o Kocoa Beam pode oferecer para **continuar a impressão de
onde parou**. É um recurso de melhor esforço: funciona melhor em peças que
ainda estão bem coladas na mesa.

O celular continua ligado (tem bateria), então a posição da impressão fica
segura mesmo com a impressora desligada.

## Como funciona

1. Durante a impressão, a posição no arquivo G-code, a posição do bico, as
   temperaturas, a ventoinha, velocidade/fluxo, o offset de Z e a malha da mesa
   são salvos em um arquivo pequeno a cada **2 segundos** (configurável).
2. Quando a impressora volta e o Klipper fica pronto, o Fluidd e o Mainsail
   mostram uma janela **"Print interrupted"** perguntando se deseja retomar.
3. Se tocar em **Resume print**, a impressora:
   1. aquece a mesa;
   2. levanta o bico, faz home **só em X e Y** e vai até o **batente do eixo X**
      (longe da peça);
   3. aquece o bico ali, para nada escorrer sobre a impressão;
   4. opcionalmente purga um pouco de filamento;
   5. volta à posição salva e continua imprimindo do byte salvo do arquivo.

**Discard** descarta a impressão. Se fechou a janela, execute
`PRINT_RECOVERY_STATUS` no console para vê-la de novo (ela também reaparece a
cada minuto enquanto houver uma impressão pendente).

## Ativar

Adicione ao `printer.cfg` (fora de outra seção) e reinicie:

```ini
[print_recovery]
```

Tudo abaixo é opcional; os valores mostrados são os padrões.

```ini
[print_recovery]
snapshot_interval: 2      # segundos entre gravações (0.5 - 300)
park_x:                   # X para aquecer/purgar (padrão: mínimo do eixo X)
park_y:                   # Y para aquecer/purgar (padrão: o do home)
park_speed: 100           # mm/s de deslocamento
lift_z: 10                # mm que o bico sobe antes do home de X/Y
purge: True               # purgar antes de continuar
purge_length: 20          # mm de filamento na purga
purge_speed: 5            # mm/s
purge_retract: 2          # mm de retração após a purga
min_extruded: 5           # mm extrudados antes da primeira gravação
prompt: True              # mostrar a janela no Fluidd/Mainsail
prompt_repeat: 60         # segundos entre lembretes (0 = uma vez)
```

Ao retomar manualmente, dá para sobrescrever valores:
`PRINT_RECOVERY_RESUME PARK_X=-6 PURGE=0 PURGE_LENGTH=10 LIFT_Z=5`.

## Comandos

| Comando | O que faz |
|---|---|
| `PRINT_RECOVERY_STATUS` | Mostra a impressão interrompida e a janela de novo |
| `PRINT_RECOVERY_RESUME` | Retoma |
| `PRINT_RECOVERY_DISCARD` | Descarta |

## Quando a impressora voltar

- Se o Klipper mostrar um erro como *"Lost communication with MCU"*, use
  **Firmware Restart**. A janela aparece quando o Klipper ficar pronto.
- **Não** mova os eixos à mão: a retomada assume que o Z não mudou.

## Limites — leia

- **O Z é assumido como inalterado.** Sem energia os motores soltam. Se o
  pórtico desceu ou foi movido, o bico pode encostar na peça. Por isso o bico
  sobe (`lift_z`) antes de qualquer movimento.
- **A peça esfria.** Pode descolar ou empenar, e costuma ficar uma linha
  visível na altura da retomada. Retome logo e só se a peça ainda estiver colada.
- Alguns segundos antes da interrupção são impressos de novo (o G-code é lido
  à frente do movimento); no máximo fica um pequeno relevo.
- A placa não tem detector de queda de energia, então o bico não consegue
  estacionar nem retrair no instante do corte.
- Se o arquivo G-code for apagado ou alterado, a retomada é recusada.
- O timelapse mantém os quadros anteriores. O Moonraker registra a retomada
  como um novo trabalho no histórico.
