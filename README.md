# Kiri Launcher

Launcher Android leve e muito personalizável, em um único app para celular, tablet e Android TV.

## Estrutura

- `core`: regras e dados em Kotlin puro, sem Android. Testável na JVM.
- `app`: o aplicativo Android (Jetpack Compose). Detecta o tipo de aparelho e mostra a interface certa.
- `personal` (opcional): um app pequeno do dono, à parte do Kiri, que não faz parte deste
  repositório. Ele mora num repositório privado, clonado nesta pasta (que o git ignora), e só
  compartilha o `core`. O Kiri nunca leva nada dele: os dois são apps separados no aparelho, que
  conversam como qualquer outro app faria (ver [fontes de cotações](docs/fontes-de-cotacoes.md)).
  `./gradlew -Pkiri.public=true ...` compila como se a pasta não existisse.

## Como compilar

Requer JDK 17 e o Android SDK (plataforma 36).

```
./gradlew :core:test :app:assembleDebug
```

O APK de teste sai em `app/build/outputs/apk/debug/`. Cada push também gera esse APK no GitHub Actions,
no artefato `kiri-debug-apk` da execução.

A versão de teste é instalada como `io.github.johankrs.kiri.debug` e assinada com a chave de debug do
repositório, então uma versão nova instala por cima da anterior.

## Releases

Cada versão tem um release no GitHub com o APK público (sem a pasta `personal`), instalado como
`io.github.johankrs.kiri` e assinado com a chave de release do Kiri, e o pacote do app para o
Google Play (`.aab`), assinado com a mesma chave. O que mudou em cada uma está no
[CHANGELOG.md](CHANGELOG.md).

Para lançar uma versão:

1. Em `gradle.properties`, subir `kiri.version` (o `versionCode` sai dela: 0.2.0 é 200).
2. No `CHANGELOG.md`, passar o que está em "Não lançado" para uma seção `## [0.2.0] - <data>`.
3. Mesclar em `main`. Quando a versão muda e ainda não tem release, o workflow `Release` compila,
   testa, confere que nada da pasta `personal` entrou, assina e publica o release `v0.2.0`, com a
   tag, as notas do changelog, o APK e o `.aab`.
4. Para o Google Play, enviar o `.aab` do release no Play Console (teste interno ou outra faixa).

Rodado à mão (Actions › Release › Run workflow), o workflow lança a versão que está na branch
escolhida, ou, se ela já tem release, compila de novo e troca o APK e as notas.

Sem o GitHub Actions, `scripts/release.sh` faz o mesmo build num computador e deixa em
`build/release/` o APK, o `.aab`, o SHA-256 de cada um e as notas, para anexar ao release à mão. Ele precisa da
chave: `KIRI_RELEASE_STORE` e `KIRI_RELEASE_PASSWORD` no ambiente, ou um `keystore.properties` na
raiz (ignorado pelo git) com `storeFile=`, `password=` e, se não for `kiri`, `alias=`.

A chave nunca entra no repositório. No GitHub ela fica em dois segredos do repositório:
`KIRI_RELEASE_KEYSTORE` (o arquivo em base64) e `KIRI_RELEASE_PASSWORD`. Todo release precisa da
mesma chave, ou o Android não instala por cima do anterior. O certificado dela tem o SHA-256
`6c9e39655b51f18c3dc0c6e7007d83d12b8830301a3a67ee033da46edb0a00c3`, que
`apksigner verify --print-certs` mostra num APK do Kiri.

## Widgets próprios

Um widget do Kiri tem duas metades:

- em `core`, um `WidgetSpec` (id e tamanhos em células) e as regras e dados, sem Android;
- em `app`, uma classe que implementa `KiriWidget` (o conteúdo em Compose e, se quiser, uma tela de
  ajustes) e uma linha em `KiriWidgets`.

Todo widget é redimensionável: entre o tamanho mínimo e o máximo do `WidgetSpec`, ele se adapta ao
espaço que recebe e continua fácil de ler. A regra é mostrar mais ou menos coisas, nunca diminuir o
texto. Como a célula muda de tamanho de um aparelho para outro, o widget decide pelo espaço real
(em dp), não pela quantidade de células.

O vidro é o mesmo para tudo que fica sobre o papel de parede: `GlassStyle` (Fumê, Cristal, Névoa) em
`core` e a receita de cores em `ui/theme/KiriGlass.kt` (`kiriGlass`, `kiriGlassEdge`, `KiriGlassPanel`).

### Mercado (ações e moedas)

Ações do Brasil e dos EUA e moedas na mesma lista, em qualquer tamanho de 2×1 a 8×8. O
`MarketPlanner` (em `core`, com testes) escolhe o que cabe: blocos lado a lado numa faixa baixa,
blocos empilhados numa coluna estreita, um item só com o preço grande num quadrado pequeno, e uma
lista de até três colunas no resto. Título, nome e gráfico só aparecem enquanto não tiram um item
da tela, e sobrando espaço tudo cresce até 130%.
Os ajustes ficam em Configurações, Widgets, Mercado.

O gráfico pequeno de cada item mostra as últimas seis horas de negociação. As horas em que o
mercado esteve fechado não contam, então ele nunca fica quase vazio logo depois da abertura, à
noite ou no fim de semana.

Tocar num item abre um painel sem sair da tela inicial: o próprio vidro do widget cresce até virar
o painel e encolhe de volta ao fechar (`MarketOverlay`), com a tela escurecida atrás. Ele cresce a
partir de onde o widget está: no alto, para baixo; embaixo, para cima; no meio, para os dois lados
(`restingPlace`). O painel é
uma camada da própria janela da tela inicial, não uma janela à parte, então o escurecimento cobre a
tela inteira, barra de status incluída, e tudo é desenhado no mesmo quadro. O cabeçalho do painel,
com voltar e atualizar, fica parado; onde a tela é baixa, o resto rola por dentro. Nos painéis
nenhum botão tem fundo: a opção escolhida é escrita mais forte e ganha um risco embaixo.

- **Ação:** preço grande, pré ou pós-mercado, máxima e mínima do dia, fechamento anterior, volume,
  máxima e mínima em 52 semanas e um gráfico grande. O gráfico abre nas últimas 24 horas (as 24
  horas que terminam no último negócio) e troca para semana, mês, ano ou 5 anos. Cada período é
  buscado só quando é escolhido e fica guardado por cinco minutos.
- **Moeda:** o conversor. Ele converte entre o real e as moedas da lista com os preços que o widget
  já buscou (`CurrencyRates` em `core`), então abrir não gasta nenhuma consulta. O valor pode ser
  digitado ou mudado com menos e mais, em passos de 1, 10, 100 ou 1.000.

Tocar nas setas no alto de qualquer painel, no título do widget (quando o tamanho deixa o título
aparecer) ou numa linha que falhou busca os preços na hora.
Dois toques seguidos em menos de cinco segundos não repetem a consulta.

As ações dos EUA mostram também o preço de antes da abertura (pré-mercado) e de depois do
fechamento (pós-mercado), com a variação contra o fechamento que ele segue. É uma linha a mais em
cada item: faz parte do item, então pode custar um item na tela, mas nunca é cortada, e só some
onde nem um item caberia inteiro com ela. Para a B3 nenhuma fonte grátis informa esses preços.

Os preços vêm de um serviço oficial e grátis para cada mercado:

| O quê | Fonte | Chave | Gráficos |
|---|---|---|---|
| Ações, FIIs, ETFs e BDRs da B3 | brapi.dev | Token grátis (PETR4, VALE3, ITUB4 e MGLU3 funcionam sem) | Até 3 meses no plano grátis |
| Ações dos EUA: preço, pré e pós-mercado | Alpaca | Chave e segredo grátis (atraso de 15 minutos) | Todos os períodos |
| Ações dos EUA sem atraso e nomes das empresas | Finnhub | Chave grátis, opcional | Nenhum no plano grátis |
| Moedas, em reais | AwesomeAPI (preço do momento) e Frankfurter (taxas de referência) | Nenhuma; com a da AwesomeAPI, o gráfico das últimas horas | Dias sem chave; horas com a chave |

Nos ajustes do widget cada serviço tem um passo a passo: um botão abre o cadastro no navegador,
a chave copiada lá é colada de volta com um toque e testada na hora, buscando um preço de verdade.
Uma chave que o serviço recusa não é guardada.

Outro app instalado no aparelho pode oferecer uma origem completa, que dá todos os mercados
sozinha (`CompleteSource` em `core`). Ela aparece como escolha ao lado dos serviços oficiais e só é
usada depois que a pessoa a escolhe. Como fazer um app assim:
[docs/fontes-de-cotacoes.md](docs/fontes-de-cotacoes.md).

Nenhuma chave vai dentro do app: cada pessoa cola a sua nos ajustes do widget, e ela fica guardada só
no aparelho. As fontes ficam atrás de `MarketRepository`, então dá para trocar uma sem mexer no widget.

## Acessibilidade

O Kiri tem um serviço de acessibilidade, só para um gesto poder bloquear a tela (o Android não deixa
um app fazer isso de outro jeito). Ele não lê nada da tela e não recebe eventos. Enquanto nenhum
gesto bloqueia a tela, o serviço nem aparece para o Android: o manifesto o deixa desativado, e o
Kiri só o oferece quando a pessoa escolhe "Bloquear a tela" para um gesto
(`KiriAccessibilityService.offer`). Assim, quem não usa esse gesto não tem nenhum serviço de
acessibilidade do Kiri no aparelho, o que alguns apps de banco exigem.

## Privacidade

O Kiri não coleta nada e não tem conta, anúncio nem análise de uso. Ver
[docs/privacidade.md](docs/privacidade.md).

## Licença

O Kiri é software livre, sob a [GNU General Public License v3.0](LICENSE). A fonte Figtree vem com
a sua própria licença, a SIL Open Font License, em [licenses/Figtree-OFL.txt](licenses/Figtree-OFL.txt).
