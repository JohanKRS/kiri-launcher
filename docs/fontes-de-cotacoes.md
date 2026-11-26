# Fontes de cotações vindas de outro app

O widget Mercado busca os preços em serviços oficiais, cada um com a chave da própria pessoa. Além
deles, qualquer app instalado no mesmo aparelho pode oferecer ao Kiri uma **fonte completa**: uma
origem que dá sozinha ações do Brasil e dos EUA, moedas e gráficos. O Kiri não leva o código dela;
só pergunta os preços ao outro app. Assim, uma fonte pode ter a sua própria licença, ser atualizada
à parte e ficar fora deste repositório.

O acordo entre os dois lados está em `core`, em `SourceApps` (`io.github.johankrs.kiri.market`),
que o app da fonte pode usar como está.

## O que o app da fonte declara

Um provedor de conteúdo (`ContentProvider`) exportado, com um filtro de intenção para a ação
`io.github.johankrs.kiri.action.MARKET_SOURCE` e dois `meta-data`:

```xml
<provider
    android:name=".MinhaFonte"
    android:authorities="com.exemplo.minhafonte"
    android:exported="true"
    android:label="@string/nome_da_fonte">
    <intent-filter>
        <action android:name="io.github.johankrs.kiri.action.MARKET_SOURCE" />
    </intent-filter>
    <!-- O nome com que a escolha fica salva nos ajustes do widget. Nunca "official". -->
    <meta-data
        android:name="io.github.johankrs.kiri.source.KEY"
        android:value="minhafonte" />
    <!-- Opcional: uma ou duas frases sobre o que a fonte cobre, traduzíveis. -->
    <meta-data
        android:name="io.github.johankrs.kiri.source.NOTE"
        android:resource="@string/sobre_a_fonte" />
</provider>
```

O rótulo do provedor é o nome que aparece em "De onde vêm os preços". Embaixo dele o Kiri mostra a
nota do app e diz de qual app a fonte vem.

## Como o Kiri pergunta

Pelo `call` do provedor, que roda numa thread do Android para chamadas de outros apps (nunca na
principal), então pode esperar a rede:

| Método | Extras que chegam | O que devolver em `result` |
|---|---|---|
| `quote` | `instrument` (ex.: `B3:PETR4`, `US:AAPL`, `FX:USD`), `version` | `SourceApps.encodeQuote(...)` |
| `series` | `instrument`, `range` (`day`, `week`, `month`, `year`, `5y`), `version` | `SourceApps.encodeSeries(...)` |

A resposta é um `Bundle` com a string `result`. Uma falha também é resposta: `QuoteResult.Failed`
com o motivo (`NETWORK`, `NOT_FOUND`, `RATE_LIMITED`...). O Kiri trata qualquer coisa que não
consiga ler como resposta ruim, e um app que não responde como sem conexão.

`instrument` usa `Instrument.id`; `Instrument.parse` lê de volta. As moedas são cotadas em reais.

## O que o Kiri faz e não faz

- Procura os apps de fonte sozinho e de novo a cada poucos segundos, então um app instalado ou
  removido aparece ou some dos ajustes sem reiniciar o Kiri.
- Nunca usa uma fonte de outro app antes de a pessoa escolhê-la. Sem escolha, ou se o app sumir,
  voltam os serviços oficiais.
- Só manda ao outro app o código do que quer o preço. Nenhuma chave, nada da tela inicial.
- O app da fonte pode conferir quem pergunta com `getCallingPackage()` (o Kiri é
  `io.github.johankrs.kiri`; o build de teste, `io.github.johankrs.kiri.debug`).
