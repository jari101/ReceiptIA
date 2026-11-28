# Formato de receita `receiptai-receita-v1` (JSON)

É o formato dos **ficheiros de receitas de cozinha** que a app sabe importar.
Usa-se em **Importar recibo → Escolher ficheiros**: um ficheiro `.json` sozinho, vários de uma vez
ou dentro de um `.zip` (no meio de recibos também funciona: a app separa-os sozinha).

As receitas são **guardadas logo** no separador **Receitas** (não passam pelo ecrã Rever) e
aparece a mensagem "3 receitas adicionadas às Receitas". Uma receita com o **mesmo nome** de
outra que já existe é saltada, para importar o mesmo ficheiro 2 vezes não duplicar nada.

## Exemplo

```json
{
  "formato": "receiptai-receita-v1",
  "nome": "Bacalhau à Brás",
  "refeicao": "jantar",
  "minutos": 30,
  "dietas": ["sem-gluten", "sem-lactose"],
  "ingredientes": [
    { "nome": "Bacalhau desfiado", "quantidade": "300 g" },
    { "nome": "Ovos", "quantidade": "5" },
    { "nome": "Salsa", "quantidade": "q.b.", "opcional": true }
  ],
  "passos": [
    "Aloura a cebola às meias-luas em azeite.",
    "Junta o bacalhau, a batata palha e os ovos batidos."
  ]
}
```

## Campos

| Campo | Obrigatório | Descrição |
|---|---|---|
| `formato` | não* | Sempre `"receiptai-receita-v1"`. |
| `nome` | **sim** | Nome da receita. |
| `refeicao` | não | `pequeno-almoco`, `lanche`, `almoco` ou `jantar` (com ou sem acentos). Por defeito: jantar. |
| `minutos` | não | Tempo de preparação (número). |
| `dietas` | não | Lista com `diabeticos`, `ginasio`, `sem-gluten`, `sem-lactose`, `vegetariano`. |
| `ingredientes` | **sim** | Pelo menos um. Cada um é um texto (`"Ovos"`) ou um objeto (ver abaixo). |
| `ingredientes[].nome` | **sim** | Nome do ingrediente. |
| `ingredientes[].quantidade` | não | Texto livre: `"300 g"`, `"2 dentes"`, `"q.b."`. |
| `ingredientes[].opcional` | não | `true` se a receita se faz **sem** este ingrediente. Na app aparece "(opcional)" e, se faltar, um balão explica que não é preciso. |
| `passos` | não | Lista de textos, ou um texto só com um passo por linha. |

\* Sem `formato`, um `.json` também é tratado como receita se tiver `ingredientes`.

- Vários de uma vez: uma **lista** (`[ {...}, {...} ]`) ou `{ "receitas": [ ... ] }`.
- Chaves em inglês também funcionam: `name`, `meal`, `minutes`, `diets`, `ingredients`, `quantity`, `optional`, `steps`.
- Uma receita sem nome ou sem ingredientes é ignorada.

Exemplos: [`exemplos/receita-exemplo.json`](exemplos/receita-exemplo.json),
[`exemplos/receitas-exemplo.json`](exemplos/receitas-exemplo.json) e o zip
[`exemplos/recibos-e-receitas-exemplo.zip`](exemplos/recibos-e-receitas-exemplo.zip), que mistura recibos e receitas.
