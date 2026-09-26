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
      (longe da peça) — opcional, veja `park_enable_x` / `park_enable_y`;
   3. aquece o bico ali, para nada escorrer sobre a impressão;
   4. opcionalmente purga um pouco de filamento — veja `purge`;
   5. volta à posição salva e continua imprimindo do byte salvo do arquivo.

**Discard** descarta a impressão e depois levanta o bico para longe da peça
abandonada (**50 mm** por padrão) e faz home de X e Y — tudo configurável, veja
`discard_lift_z`, `discard_home_x` e `discard_home_y`. Se fechou a janela, execute
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
park_enable_x: True       # True: home de X e aquece o bico no batente do X
park_enable_y: True       # True: também home de Y (False: eixo não se move)
park_x:                   # X para ir após o home de X (padrão: mínimo do X)
park_y:                   # Y para ir após o home de Y (padrão: fica no home)
park_speed: 100           # mm/s de deslocamento
lift_z: 10                # mm que o bico sobe antes do home de X/Y
discard_lift_z: 50        # mm que o bico sobe ao usar Discard (0 = não sobe)
discard_home_x: True      # home de X após Discard
discard_home_y: True      # home de Y após Discard
purge: True               # True/False: purgar antes de continuar (só se X
                          # ou Y estiver estacionado)
purge_length: 20          # mm de filamento na purga
purge_speed: 5            # mm/s
purge_retract: 2          # mm de retração após a purga
min_extruded: 5           # mm extrudados antes da primeira gravação
language: auto            # idioma da janela/console (veja abaixo)
macro_variables: *        # variáveis de macro a salvar/restaurar: * = todos os
                          # macros que não começam com _, vazio = nenhum, ou
                          # uma lista: PRINT_START, MEU_MACRO
prompt: True              # mostrar a janela no Fluidd/Mainsail
prompt_repeat: 60         # segundos entre lembretes (0 = uma vez)
```

Ao retomar manualmente, dá para sobrescrever valores:
`PRINT_RECOVERY_RESUME PARK_ENABLE_X=1 PARK_ENABLE_Y=0 PARK_X=-6 PURGE=0
PURGE_LENGTH=10 LIFT_Z=5` (`*_ENABLE_*`, `PURGE` e, no Discard, `HOME_X` /
`HOME_Y` aceitam `1`/`0` ou `True`/`False`; o Discard também aceita `LIFT_Z`). O que não for informado
usa o valor do `printer.cfg` ou o padrão acima.

### O que fazem `park_x` e `park_y`

Depois de uma queda de energia a impressora não sabe mais onde estão X e Y,
então a retomada faz home deles primeiro. Quando um eixo faz home
(`park_enable_x` / `park_enable_y`), o bico vai para `park_x` / `park_y` e
aquece (e purga) ali, longe da impressão:

- `park_x` — posição X usada para aquecer e purgar. Vazio = mínimo do X da
  máquina, ou seja, o batente do X (na Neptune 3 Pro, `-6`, à esquerda da mesa).
- `park_y` — o mesmo para o Y. Vazio = "fica onde o home do Y deixou o bico"
  (normalmente a frente da mesa).

Só configure se o canto padrão não for um bom lugar para escorrer filamento.

### Não mover um eixo (`park_enable_x: False` / `park_enable_y: False`)

Um eixo em `False` **não** faz home nem se move: assume-se que está exatamente
onde estava. Com os dois em `False`, o bico só sobe `lift_z` e aquece no lugar,
sobre a peça, e a purga é ignorada (derramaria filamento na impressão). Use só
se tiver certeza de que nada se moveu; o bico pode escorrer um pouco.

### Idioma

A janela e as mensagens do console seguem o idioma escolhido no Fluidd ou no
Mainsail (inglês, português, russo, chinês simplificado/tradicional; qualquer
outro mostra inglês). Use `language: pt` (ou `en`, `ru`, `zh`, `zh-TW`) para
forçar um idioma.

## Variáveis de macro

Se seus macros guardam estado em variáveis (`variable_xxx:` na seção deles, por
exemplo um macro inicial ou um contador de camada), os valores do último
snapshot são salvos e recolocados antes de reabrir o arquivo. Só valores
simples (números, texto, `True`/`False`, listas) são mantidos. Isso não desfaz
o que um macro fez fora das variáveis. Use `macro_variables` para salvar só
alguns macros ou nenhum.

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
- **Impressoras Delta não são suportadas:** elas não fazem home de X e Y
  separadamente.
- **`G28` personalizado:** se sua configuração altera o home (por exemplo, um
  `homing_override` que também faz home do Z), a etapa de home de X/Y da
  retomada pode se comportar mal. Confira se `G28 X Y` faz home só de X e Y.
- **Multifilamento e multiferramenta** (MMU/Happy Hare, IDEX, trocadores de
  ferramenta) não são tratados: só o extrusor ativo é salvo e restaurado.
- **Ventoinhas:** só a ventoinha da peça (`[fan]`) é restaurada. As demais
  (`fan_generic`, do controlador ou auxiliares) voltam ao que sua configuração
  ou macros definirem.
- **Câmara:** aquecedores de câmara e suas temperaturas não são salvos nem
  restaurados.
- O timelapse mantém os quadros anteriores. O Moonraker registra a retomada
  como um novo trabalho no histórico.
