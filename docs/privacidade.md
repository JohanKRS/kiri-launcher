# Política de privacidade do Kiri Launcher

Última atualização: 27 de novembro de 2026.

O Kiri Launcher (`io.github.johankrs.kiri`) é um launcher de código aberto para Android. Ele não
coleta, não guarda fora do aparelho e não compartilha dados pessoais. Não tem conta, login,
anúncios nem análise de uso, e não usa nenhum serviço de terceiros para isso.

## O que fica no aparelho

Tudo o que o Kiri guarda fica só no aparelho, nos dados do próprio app: a tela inicial, os ajustes,
os ícones escolhidos, a lista de ações e moedas do widget Mercado e as chaves que a pessoa colar
para os serviços de cotações. O backup automático do Android (da conta Google da pessoa) pode levar
esses dados, como faz com qualquer app; o Kiri não tem acesso a esse backup. O backup feito pelo
próprio Kiri é um arquivo que a pessoa salva onde escolher, e as chaves só entram nele, trancadas
com senha, se ela ligar essa opção.

## Permissões e para que servem

- **Ver os apps instalados**: para mostrar os apps na gaveta e na tela inicial, os widgets que eles
  oferecem e os pacotes de ícones. A lista nunca sai do aparelho.
- **Internet**: só o widget Mercado usa, para buscar preços nos serviços que a pessoa escolheu
  (brapi.dev, Alpaca, Finnhub, AwesomeAPI, Frankfurter). Esses pedidos levam só o código da ação
  ou moeda e, quando o serviço pede, a chave da própria pessoa. Cada serviço tem a sua própria
  política de privacidade.
- **Acesso a todos os arquivos** (opcional): o Android só entrega a imagem do papel de parede a
  apps com essa permissão. O Kiri a usa só para ler o papel de parede e desfocar a sua cópia atrás
  do vidro, e para achar os backups do próprio Kiri salvos no aparelho. Ele não lê nem envia nenhum
  outro arquivo.
- **Fotos** (só na TV, opcional): para escolher uma imagem do aparelho como fundo da tela inicial.
- **Papel de parede**: para aplicar a imagem que a pessoa escolher no Kiri.
- **Serviço de acessibilidade** (opcional): só para um gesto poder bloquear a tela. Ele não lê nada
  da tela, não recebe eventos e só aparece no Android quando a pessoa escolhe esse gesto. Antes de
  abrir a Acessibilidade, o Kiri explica isso numa tela à parte e só segue se a pessoa permitir.
- **Abrir a barra de notificações** e **pedir para desinstalar um app**: ações que a pessoa toca.

A versão do Google Play não pede acesso a todos os arquivos, a lista completa dos apps nem as
fotos. Nela o vidro desfoca só um papel de parede escolhido no próprio Kiri.

## Outros apps

Um app instalado à parte pode oferecer cotações ao widget Mercado. O Kiri só pergunta a ele o preço
do que está na lista, e só depois que a pessoa escolhe esse app como fonte.

## Contato

Dúvidas ou pedidos: abra uma issue em https://github.com/JohanKRS/kiri-launcher/issues.
