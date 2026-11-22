# Formato de recibo `receiptai-v1` (JSON)

É o formato "oficial" da app ReceiptAI. Serve para duas coisas:

1. **Importar recibos digitais** na app (ficheiro `.json`, link ou "Partilhar com ReceiptAI").
2. **Protocolo robô → app**: depois de ler o recibo com a câmara e a IA, o robô
   (Raspberry Pi) envia exatamente este JSON para o telemóvel.

## Exemplo

```json
{
  "formato": "receiptai-v1",
  "loja": "Mercado do Bairro",
  "data": "2026-11-18",
  "total": 6.25,
  "itens": [
    { "nome": "Leite meio gordo 1L", "quantidade": 2, "preco_unitario": 0.89, "total": 1.78 },
    { "nome": "Bananas", "quantidade": 0.85, "preco_unitario": 1.29, "total": 1.10 },
    { "nome": "Café moído 250g", "quantidade": 1, "preco_unitario": "3,37" }
  ]
}
```

## Campos

| Campo | Obrigatório | Descrição |
|---|---|---|
| `formato` | não | Sempre `"receiptai-v1"`. |
| `loja` | não | Nome da loja (se faltar: "Loja desconhecida"). |
| `data` | não | `AAAA-MM-DD` ou `DD/MM/AAAA` (se faltar: hoje). |
| `total` | não | Total impresso no recibo, em euros. Serve para confirmar a soma. |
| `itens` | **sim** | Lista de produtos. |
| `itens[].nome` | **sim** | Nome do produto. |
| `itens[].quantidade` | não | Unidades ou kg (por defeito 1). |
| `itens[].preco_unitario` | um dos dois | Preço por unidade/kg. |
| `itens[].total` | um dos dois | Preço da linha. Se faltar, é `quantidade × preco_unitario`. |

- Os preços podem ser números (`0.89`) ou texto (`"0,89 €"`).
- Também se aceita uma **lista** de recibos (`[ {...}, {...} ]`) ou `{ "recibos": [ ... ] }`.
- Chaves em inglês também funcionam: `store`, `date`, `items`, `name`, `quantity`, `price`.

Dentro da app, os valores são sempre guardados em **cêntimos** (números inteiros), para evitar erros de arredondamento.

## Outros formatos aceites

| Formato | Como é lido |
|---|---|
| PDF | Texto do PDF (PdfBox). Se for um PDF digitalizado, as páginas passam pelo OCR (ML Kit). |
| Imagem / câmara | OCR do Google ML Kit, no telemóvel e sem Internet. |
| HTML (página web) | O texto da página, mantendo cada linha da tabela. |
| TXT | Texto do recibo. |
| CSV | Colunas `data;loja;produto;quantidade;preco` (cabeçalho flexível; separador `;`, `,` ou tab). |
| XML | Fatura eletrónica **UBL 2.1 / CIUS-PT** ou ficheiro **SAF-T PT**. |

Tens exemplos de todos estes formatos em [`docs/exemplos/`](exemplos/).
