# Changelog

Tudo o que muda no Kiri, versão por versão, da mais nova para a mais antiga. Cada versão vira
uma tag `v<versão>` e um release no GitHub com o APK. Os números entre parênteses são os PRs.

A versão fica num lugar só, `kiri.version` em `gradle.properties`, no formato
`MAIOR.MENOR.CORREÇÃO`. Enquanto o Kiri estiver em 0.x, qualquer versão nova pode mudar bastante
coisa. Como lançar uma versão: ver "Releases" no README.

## [Não lançado]

### Papéis de parede animados
- Seis papéis de parede animados que o próprio Kiri desenha: Aurora viva, Bruma, Fios de névoa,
  Pastilhas de luz, Serra na névoa e Flores de kiri. Cada um vem nas seis cores do ícone do Kiri
  e com movimento Normal, Lento ou Parado. Não são vídeo nem imagem: cada cena é desenhada pequena,
  separada do resto da tela inicial, e a tela estica. Por isso pesam pouco até numa TV fraca, e
  param de desenhar quando não estão à vista.
- Na TV, a Aurora viva é o novo fundo padrão da tela inicial. Quem estava com a Aurora de antes, que
  era o padrão, passa para a viva; a de antes continua na lista como "Aurora parada", e os outros
  fundos escolhidos ficam como estão. Em Tela da TV aparecem Cor e Movimento quando o fundo é
  animado. Na cor Névoa, que é clara, o relógio, a engrenagem e os nomes dos banners ficam escuros.
- No celular e no tablet, Papel de parede ganha "Papel de parede animado do Kiri": escolha a cena,
  a cor e o movimento e toque em "Usar como papel de parede", e o Android mostra o papel e pede para
  confirmar. Depois disso, as mudanças aparecem na hora. O vidro do Kiri desfoca uma imagem parada
  da cena, então o desfoque funciona com ele mesmo na versão da Play Store.

### Configurações
- "Tema" em Configurações › Aparência: Do sistema, Claro ou Escuro. As próprias configurações
  mudam na hora. Sem escolha, o celular segue o aparelho e a TV fica no escuro, porque quase
  nenhuma TV tem modo escuro e muita gente assiste com a luz apagada (#16).
- Em cada tela de configurações, os seletores dão ao valor o mesmo espaço, o do maior valor da
  tela, e assim o menos e a seta para a esquerda de todas as linhas ficam numa coluna só, como o
  mais e a seta para a direita (#16).

### TV
- Botão Home: em Configurações › Botão Home (e sozinho, uma vez, quando o Kiri abre na TV pela
  primeira vez) o Kiri mostra o que o Home do controle abre e se oferece para ser a tela inicial.
  Onde o Android deixa escolher, ele pede do jeito do Android. Onde a tela inicial de fábrica tem
  prioridade sobre qualquer escolha, como a do Google TV, o Kiri vem no lugar dela sempre que ela
  aparece, pelo mesmo serviço de acessibilidade do toque duplo: só depois de você permitir, com
  aviso do que o serviço faz e não faz, e desligável na mesma tela. O serviço só fica sabendo
  quando uma janela da tela inicial de fábrica aparece, e de nenhum outro app (#14).
- Uma TV que lista o mesmo sintonizador duas vezes (uma Hisense lista "Tuner" duas vezes) mostra
  uma entrada só (#14).
- Na TV, as configurações mostram só o que vale para a TV. Saem Papel de parede (o da TV fica em
  Tela da TV), Ícones e Tipografia, que eram dos ícones e nomes do celular, e "Tela da TV" passa a
  ser a primeira linha de Aparência. Em Animações saem a gaveta e o "Voo em onda", que na TV era igual ao "Voo
  de volta" (#15).
- Tela da TV ganha "Espaço entre os banners" (7 níveis; o 4 é o de antes) e "Sombra" (Nenhuma,
  Leve, Média, Forte), que vale para os banners e os widgets. Antes a única sombra era a dos ícones
  do celular, e desligá-la não mudava nada na TV (#15).
- "Revelar do banner": na TV o app abre a partir do banner dele, e a tela inicial volta para o
  banner, como o celular faz com o ícone (#15).
- "Ícone do Kiri" vira "Banner do Kiri" na TV e mostra os banners de cada cor, que são o que a TV
  mostra na lista de apps (#15).
- Os ajustes do widget Mercado, abertos pelo menu do widget na TV, mostram o widget como a TV o
  desenha e só o que vale lá: atualizar a cada, a lista e de onde vêm os preços. Tamanho de teste,
  vidro, cantos e gráfico, que são do widget do celular, ficam de fora (#15).

## [0.3.0] - 2026-11-27

### Tela inicial
- Um app que troca o próprio ícone por dentro (os que oferecem vários ícones para escolher)
  continua no mesmo lugar da tela inicial ou da pasta, com o nome, o ícone e o fundo que você
  escolheu para ele, em vez de sumir da tela inicial e ficar só na gaveta. Vale também para o
  gesto que abre o app, para a tela inicial da TV e para um app cuja atualização muda a tela
  por onde ele abre (#10).

### Google Play
- A ficha da loja fica no repositório, em `fastlane/metadata/android/pt-BR/`: nome, descrições,
  ícone, imagem de destaque, banner da TV e capturas do celular e da TV. A ficha inteira também
  existe em inglês, em `en-US/`, e as notas desta versão para a Play ficam em `changelogs/300.txt`
  nos dois idiomas (#12).

### Gaveta
- Abrindo ou fechando a gaveta, a borda de cima da folha não é mais uma linha reta: o vidro
  (desfoque e cor) vai de transparente até a cor da gaveta num degradê suave, que acompanha o
  dedo. Com a gaveta toda aberta, a tela inteira continua com o mesmo vidro (#8).

### Movimento
- Duas maneiras novas de abrir e fechar a gaveta, acompanhando o dedo como as outras: **Revelar
  do dedo** (o vidro se abre num círculo de borda suave a partir de onde o dedo começou, e cada
  app aparece quando o círculo passa por ele) e **Cascata** (o vidro embaça no lugar e os apps
  sobem fileira por fileira, de baixo para cima). "Deslizar de baixo" virou **Subir** e "Zoom"
  virou **Aproximar** (#9).
- A tela inicial sai e volta do mesmo jeito para a gaveta e para os apps, com cinco maneiras:
  **Recuar** (a de antes), **Voo de volta** (ganha zoom por fora e volta voando rapidinho para o
  lugar, com um leve assentar; ao abrir um app, o zoom sai do ícone tocado), **Voo em onda**
  (cada item voa sozinho e os mais perto chegam primeiro), **Foco** (desfoca como o fundo de uma
  foto) e **Esmaecer**, além de **Nenhuma**. Enquanto um app abre, a tela não some, para nunca
  ficar vazia se o app demorar (#9).
- Abrir um app a partir do ícone, que mostra o app num recorte que se abre do ícone, passou a se
  chamar **Revelar do ícone**. Quem desenha o app abrindo é o Android (#9).
- As escolhas de Animações são passadas com as setas, como os outros nomes das configurações, com
  o nome em cima e as setas alinhadas de uma linha para a outra (#9).

### Idiomas
- O Kiri inteiro em inglês revisado, para quem usa o aparelho em inglês: os textos foram
  reescritos para soar natural (configurações, menus, backup, pastas, Mercado e a TV). Em
  português nada muda, a não ser dois avisos que citavam nomes antigos de telas (#13).
- No Android 13 ou mais novo dá para escolher o idioma só do Kiri, sem mudar o do aparelho:
  Configurações › Apps › Kiri › Idioma (inglês ou português) (#13).

## [0.2.1] - 2026-11-27

### Gestos
- Antes de abrir a Acessibilidade do Android, o Kiri mostra numa tela à parte para que serve o
  serviço (só bloquear a tela quando você faz o gesto), o que ele não faz (não lê a tela, não vê
  o que você digita, não coleta nem envia dados) e como desligar, com "Permitir" e "Agora não".
  Escolher "Bloquear a tela" para um gesto só vale depois de "Permitir" (#11).

### Google Play
- A versão do Google Play volta a ter o serviço de acessibilidade, então o toque duplo pode
  bloquear a tela também nela. O Play Console pede para ele uma declaração com um vídeo do aviso
  acima. Seguem fora dela o acesso a todos os arquivos, a lista de todos os apps e as fotos (#11).

## [0.2.0] - 2026-11-26

Primeira versão também para o Google Play: o release traz, além do APK, o pacote do app
(`kiri-launcher-0.2.0.aab`) para enviar no Play Console.

### Pastas
- A pasta aberta tem uma casa quadrada para cada app: os ícones ficam à mesma distância na
  horizontal e na vertical, e o painel tem a mesma margem nos quatro lados. Com 4, 9 ou 16 apps
  (e com 3, 7 ou 8, que fecham a mesma grade) o painel é um quadrado. O nome da pasta vai numa
  etiqueta do mesmo vidro logo acima do painel, e tocar nele continua renomeando (#6).
- Com muitos apps para a largura da tela, o espaço entre as linhas diminui antes dos ícones,
  que ficam quase do tamanho dos da tela inicial (#6).
- Segurar um app dentro da pasta aberta mostra o mesmo menu da tela inicial (editar, esconder,
  informações, desinstalar...). Arrastando, o menu some e o app muda de lugar na pasta, como
  antes (#7).
- Com a pasta aberta, arrastar para cima ou para baixo fora do painel fecha a pasta e faz o gesto
  da tela inicial: a gaveta sobe acompanhando o dedo, ou descem as notificações. Arrastar o
  próprio painel continua fechando só a pasta (#7).

### Widgets
- Tocar e segurar num widget acende o vidro até a borda, acompanhando os cantos arredondados,
  sem o corte reto de antes: vale para o Mercado e para os widgets de outros apps que usam o
  vidro do Kiri. Botão com forma própria (uma pílula, um círculo) continua com a luz dele (#3).
- Mercado: quando há mais itens do que cabem, o widget rola com o dedo e sempre para num item
  inteiro, nunca pela metade, em qualquer tamanho. Uma marca fina à direita avisa que há mais (#3).
- Mercado: um app instalado à parte pode oferecer ao widget uma fonte completa de preços (ações
  do Brasil e dos EUA, moedas e gráficos). Ela aparece em "De onde vêm os preços", ao lado dos
  serviços oficiais, dizendo de qual app vem, e só vale depois de escolhida; se o app for
  removido, voltam os serviços oficiais. Como fazer um app assim:
  [docs/fontes-de-cotacoes.md](docs/fontes-de-cotacoes.md) (#5).

### Gestos
- O serviço de acessibilidade do Kiri só existe para o Android enquanto um gesto bloqueia a tela.
  Sem esse gesto ele some da lista de acessibilidade do aparelho, e os apps que se recusam a abrir
  ao lado de um serviço assim (alguns bancos) deixam de ver um no Kiri. Escolher de novo
  "Bloquear a tela" o traz de volta à lista e abre a página dele, onde é preciso ligá-lo outra
  vez: o Android esquece que estava ligado (#5).

### Google Play
- O Kiri passa a mirar o Android 16 (API 36), como o Google Play exige de todo app novo desde
  31 de agosto de 2026 (#5).
- Cada release traz também o `.aab`, assinado com a chave de release, e `scripts/release.sh`
  confere a assinatura dele como confere a do APK (#5).
- O `.aab` é a versão do Google Play: o mesmo Kiri sem o que o Play pede para declarar e justificar
  antes de publicar até um teste (acesso a todos os arquivos, ver todos os apps, fotos e o serviço
  de acessibilidade). Nela o vidro desfoca o papel de parede escolhido no Kiri, nenhum gesto
  bloqueia a tela, a TV escolhe a imagem por outro app e os widgets de apps sem ícone não
  aparecem; cada tela que ofereceria uma dessas coisas diz isso. O APK continua com tudo (#5).
- Política de privacidade: [docs/privacidade.md](docs/privacidade.md) (#5).

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
