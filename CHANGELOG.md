# Changelog

Tudo o que muda no Kiri, versão por versão, da mais nova para a mais antiga. Cada versão vira
uma tag `v<versão>` e um release no GitHub com o APK. Os números entre parênteses são os PRs.

A versão fica num lugar só, `kiri.version` em `gradle.properties`, no formato
`MAIOR.MENOR.CORREÇÃO`. Enquanto o Kiri estiver em 0.x, qualquer versão nova pode mudar bastante
coisa. Como lançar uma versão: ver "Releases" no README.

## [Não lançado]

### Widgets
- Tocar e segurar num widget acende o vidro até a borda, acompanhando os cantos arredondados,
  sem o corte reto de antes: vale para o Mercado e para os widgets de outros apps que usam o
  vidro do Kiri. Botão com forma própria (uma pílula, um círculo) continua com a luz dele (#3).
- Mercado: quando há mais itens do que cabem, o widget rola com o dedo e sempre para num item
  inteiro, nunca pela metade, em qualquer tamanho. Uma marca fina à direita avisa que há mais (#3).

## [0.1.0] - 2026-11-26

Primeira versão pública. Reúne tudo o que foi feito desde o começo do projeto, antes de o código
vir para este repositório.

O APK é o app `io.github.johankrs.kiri`, assinado com a chave de release do Kiri.

### Tela inicial
- Páginas de apps com grade ajustável (linhas, colunas, margem e tamanho do ícone) e prévia ao
  vivo. Arrastar move um app ou troca dois de lugar.
- Nome de app nunca é cortado: quebra em mais linhas, e todas as linhas da grade ficam com a
  mesma altura.
- Grade "margem manda": os ícones vão de uma margem à outra, com "Mesmo espaço na vertical" ou um
  "Espaço vertical" à parte, e "Posição da grade" em cima, no meio ou embaixo.
- Mudar linhas ou colunas não bagunça mais a tela: cada página guarda a grade em que foi
  arrumada, o que está embaixo continua embaixo, e voltar ao número antigo põe tudo no lugar.
- Tela "Páginas": adicionar (inclusive vazia), tirar, mudar a ordem e escolher a página inicial,
  aberta pelo menu de um espaço vazio, com movimento calmo e o papel de parede parado.
- Menu ao segurar num espaço vazio: Widgets, Papel de parede, Páginas e Configurações.
- Gestos configuráveis (deslizar para cima e para baixo, toque duplo, segurar), cada um com uma
  ação: gaveta, notificações, widgets, páginas, configurações, abrir um app ou "Bloquear a tela".
  Bloquear usa o serviço de acessibilidade do Kiri, desligado até a pessoa ligar.
- Retrato e paisagem com arranjos separados.
- Tudo acompanha o dedo e termina com mola: arrastar, gaveta, pastas, páginas e painéis.

### Gaveta
- Gaveta em tela cheia, sem busca, que acompanha o dedo. O fundo chega antes e os ícones assentam
  por último.
- Vidro, opacidade, desfoque e tinta ajustáveis.
- Segurar um app na gaveta e arrastar direto para a tela inicial.
- App recém-instalado entra só na gaveta, nunca sozinho na tela inicial.

### Apps e ícones
- Menu ao segurar um app: pôr ou tirar da tela inicial, "Editar ícone e nome", "Esconder app" e
  os atalhos Informações, Play Store e Desinstalar.
- Renomear app, valendo na tela inicial, na gaveta e nos menus.
- Trocar o ícone de um app por um de pacote de ícones. Os pacotes ficam fechados e a busca acha o
  app pelo nome em todos eles.
- Ícones com forma geométrica (círculo, cantos arredondados, squircle), vidro, borda lisa ou com
  relevo e a faixa de luz ao rolar. O tamanho do desenho se ajusta no geral e por app, e um ícone
  adaptativo pode trocar o próprio fundo pelo vidro.
- Esconder apps pelo menu do app ou em Configurações › Organização › Esconder apps.
- Tudo é por app: mudar o ícone ou o nome na tela inicial muda na gaveta, e vice-versa.

### Pastas
- Soltar um app sobre outro cria uma pasta, já com nome quando o Android diz que os dois são do
  mesmo tipo.
- Fechada, mostra as miniaturas no melhor encaixe, sobre vidro ou sem fundo, e "+N" quando passa
  do limite. Aberta, é um painel de vidro que nasce da pasta e acompanha o dedo.
- Renomear, mudar a ordem, tirar um app arrastando, desfazer a pasta, e Configurações › Pastas
  com prévia ao vivo.
- O teclado ao renomear não espreme mais a tela, há ajustes de espaço entre os apps e de margem,
  e a pasta vira painel sem pulo. Abre sem o zoom da tela inicial, com os ícones do mesmo
  tamanho dos dela.

### Widgets
- Tela de widgets própria: os que estão na tela inicial e todos os que os apps oferecem,
  agrupados por app.
- Widgets de outros apps na tela inicial, com a permissão e a configuração do próprio widget,
  mover, redimensionar e remover. Apps com widgets e sem ícone, como o Clima da Samsung,
  agora aparecem.
- "Aparência" veste um widget de outro app com o vidro do Kiri: ocultar o fundo dele, usar as
  cores do Kiri, a Figtree e afastar o conteúdo da borda.
- Os widgets do Kiri se redimensionam e se adaptam ao espaço, sem nunca diminuir o texto.

### Mercado (ações e moedas)
- Ações do Brasil e dos EUA e moedas numa lista só, em qualquer tamanho de 2×1 a 8×8.
- Conversor de moedas com os preços que o widget já buscou.
- Pré e pós-mercado das ações dos EUA, gráfico das últimas 6 horas de negociação e atualização
  na hora tocando no título, sem número quebrado no widget pequeno.
- Painel da ação que nasce do próprio widget, com gráfico de 24 horas, semana, mês, ano e 5 anos.
  Escurece a tela inteira e cresce a partir de onde o widget está.
- Fontes oficiais, todas com gráfico: brapi (B3), Alpaca e Finnhub (EUA), AwesomeAPI e
  Frankfurter (moedas), com passo a passo das chaves no app e teste na hora. Nenhuma chave
  vem no app: cada pessoa cola a sua, que fica só no aparelho.

### Android TV
- Tela inicial própria: banners em grade ou carrossel, à esquerda, no centro ou justificados, em
  três tamanhos, com papéis de parede desenhados pelo Kiri.
- Entradas da TV (antena, HDMI, AV) como itens, com banners prontos de consoles e PC em cinco
  estilos e um editor de banner e nome.
- "Todos os apps", foco do controle que não fica preso, a engrenagem e a tecla Menu abrindo as
  configurações, e a tela "Tela da TV" com prévia ao vivo.
- Widgets Relógio e Mercado no alto, com mover, tamanho e tirar pelo controle.
- Papel de parede com uma imagem sua, da TV ou de um pendrive.

### Aparência
- Vidro Fumê, Cristal e Névoa, numa receita só para painéis, widgets, ícones e menus, com o
  desfoque do papel de parede feito pelo próprio Kiri (funciona onde o Android não desfoca) e o
  vidro vivo nos menus.
- Figtree em tudo, com tamanho e peso dos nomes dos apps ajustáveis.
- Papel de parede escolhido e enquadrado pelo Kiri.
- Ícone do próprio Kiri, "Fios de névoa", em seis cores, em Configurações › Aparência › Ícone do
  Kiri.
- Transições à escolha ao abrir um app, ao voltar para a tela inicial e ao abrir a gaveta.
- Configurações em blocos sobre o papel de parede desfocado. Seletores sem slider: ‹ › para
  nomes e − + para números, sempre no mesmo lugar.

### Backup
- Backup de tudo num arquivo `.kiri`, salvo pelo seletor do Android (o Google Drive incluído), e
  restauração por partes, com "Voltar ao que estava antes".
- As chaves do Mercado podem ir no backup, trancadas com uma senha.
- Widget que o Android esqueceu depois de formatar vira "Toque para colocar de novo".

### Desempenho
- Vidro, ícones e desfoque de 3 a 8 vezes mais rápidos, menos memória, cuidado com TVs de pouca
  memória e tela inicial e gaveta prontas ao voltar das configurações.
- Papel de parede nítido, na resolução da tela.

### Permissões
Cada uma precisa de justificativa na Play Store:
- `QUERY_ALL_PACKAGES`, para ver todos os apps e todos os widgets.
- Serviço de acessibilidade, só para bloquear a tela, desligado até a pessoa ligar.
- `MANAGE_EXTERNAL_STORAGE`, opcional, para ler o papel de parede e desfocá-lo.
- `READ_MEDIA_IMAGES`, para a imagem sua na TV.
- `REQUEST_DELETE_PACKAGES`, para desinstalar pelo menu.
- `INTERNET`, para os preços do Mercado.

### Ainda não visto em aparelho
A tela da TV, o backup, a grade nova, o ícone do Kiri, as páginas calmas e o bloqueio da tela só
foram testados na nuvem.
