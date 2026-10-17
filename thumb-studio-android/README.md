# Thumb Studio para Android — protótipo v1

Projeto Android nativo escrito em **Java**, com **três exemplos originais de thumbnails 16:9** fornecidos pelo criador, um **quarto modelo personalizado**, um **montador de imagens sem IA** e uma **integração opcional com a OpenAI por um backend Python**.

> **Estado do projeto:** código-fonte inicial, com testes automatizados do backend. O APK não foi compilado nem testado em dispositivo nesta entrega; faça a compilação pelo GitHub Actions ou Android Studio e valide a interface no telefone.

## Modelos cadastrados

| ID | Modelo | Campos exigidos | Texto |
|---|---|---|---|
| 1 | Split-screen vertical com personagem central | Cenário esquerdo, cenário direito, personagem (3) | Título + rótulos esquerda/direita |
| 2 | Personagem mostrando algo e texto atrás | Personagem e objeto/fundo (2) | Título opcional |
| 3 | Personagem esquerda, capa direita, fundo | Personagem, capa do jogo, fundo (3) | Título opcional |
| 4 | Personalizado por referência | Uma thumbnail-modelo + até 3 imagens ou texto | Título/instruções opcionais |

As três imagens originais estão em `app/src/main/assets/modelo_1.png`, `modelo_2.png` e `modelo_3.png`. A galeria usa diretamente esses arquivos, sem os redesenhar.

## Recursos disponíveis

- Seleção de modelo com miniaturas originais.
- Seleção de imagens no armazenamento do Android, sem pedir permissão global de galeria.
- Descrição em linguagem natural orientando a IA.
- Caixa de texto para título, chave liga/desliga para adicionar texto e rótulos extras do split-screen.
- Referência personalizada que indica à IA o layout desejado e imagens/texto de substituição.
- Prévia local dos modelos 1–3, renderizada por camadas, evitando recriar os pixels com IA.
- Exportação em PNG por meio do seletor de arquivos do Android.
- Requisição HTTPS ao servidor de geração, sem expor a chave da OpenAI no aplicativo.

### Diferença importante: montagem local x IA

**Montagem sem IA:** usa os arquivos enviados, sem regenerar rostos, personagens, capas e logos. Há **redimensionamento, recorte, sobreposição e posicionamento fixo**; não há remoção inteligente de fundos. Para personagens recortados, envie PNG transparente. A descrição livre não altera a montagem local. A prévia e exportação locais são **1280×720**.

**Gerar com IA:** envia o modelo original como referência, as imagens de conteúdo e as instruções para a API de edição de imagens. A IA tenta preservar aparência e composição, mas **não há garantia de fidelidade exata**. O resultado é **1536×864 (16:9)**. O modelo 4 funciona apenas nesta modalidade.

## Compilar o APK no GitHub (método sugerido)

1. Crie um **repositório** no GitHub e coloque **todo o conteúdo desta pasta** na raiz dele (incluindo `.github/workflows/android-apk.yml`). Não envie somente o ZIP como um único arquivo.
2. Em **Actions**, selecione **Compilar APK** e clique em **Run workflow**. Se o Actions estiver desabilitado, habilite-o nas configurações do repositório.
3. Após a execução, entre no workflow concluído e baixe o artefato **`thumb-studio-debug-apk`**.
4. Extraia o ZIP desse artefato e instale o arquivo `app-debug.apk` no telefone Android, autorizando instalações dessa origem se necessário.

O workflow usa JDK 17, Android SDK 35, Gradle 8.10.2 e Android Gradle Plugin 8.7.3. Este APK é **debug**, indicado para testes, não para publicação direta na Play Store.

**No Android Studio:** abra a pasta-raiz do projeto e sincronize o Gradle; use **Build > Build APK(s)**. Se a instalação não encontrar o Gradle automaticamente, configure o Gradle 8.10.2 ou use o workflow acima.

## Como preparar o servidor da geração por IA

A API **não precisa estar rodando** para usar a montagem local. Para gerar com IA, o smartphone precisará alcançar seu servidor por **HTTPS**.

Em um servidor ou ambiente Python 3.11+:

```bash
cd server
python -m venv .venv
# Linux/macOS:
source .venv/bin/activate
# Windows PowerShell:
# .venv\\Scripts\\Activate.ps1
pip install -r requirements.txt
```

Configure as variáveis de ambiente **no servidor** (não escreva suas credenciais no código ou no APK):

```text
OPENAI_API_KEY=sua_chave_da_plataforma_openai
APP_TOKEN=crie_um_token_longo_e_secreto
OPENAI_IMAGE_MODEL=gpt-image-2
```

Inicialize:

```bash
uvicorn app:app --host 127.0.0.1 --port 8000
```

Acesse `GET /health` para conferir se o serviço está ativo. Para usar no celular, publique o serviço em uma infraestrutura com **HTTPS**, como um host Python com certificado TLS e autenticação. Uma execução `127.0.0.1` só responde no próprio computador; não basta colocar `localhost` no aplicativo do celular. O app **bloqueia endereços HTTP**.

No aplicativo, informe a URL HTTPS do servidor, por exemplo `https://seu-dominio.com`, e o valor de `APP_TOKEN` no campo **Token privado do seu servidor**, escolha as imagens e toque em **Gerar com IA**.

**Importante:** o campo token é um segredo de acesso **do servidor privado do seu app**; não é a senha do ChatGPT nem a API key da OpenAI.

## ChatGPT Plus e login

O ChatGPT Plus **não inclui automaticamente consumo da API**. Existe um mecanismo oficial separado chamado **Sign in with ChatGPT** para produtos participantes, com compartilhamento opcional de determinadas cotas do plano, mas a disponibilidade ainda é restrita e nem toda funcionalidade de API está necessariamente incluída. Por isso, a v1 **não possui login ChatGPT/Plus**. Usa API de imagens separadamente, com cobrança conforme a conta e o modelo de API. Fontes oficiais: https://help.openai.com/en/articles/6950777-what-is-chatgpt-plus e https://developers.openai.com/siwc.

## Segurança e custos

- Não coloque `OPENAI_API_KEY` no Android. Ela só deve existir no ambiente do servidor.
- `APP_TOKEN` é uma proteção **simples para o protótipo pessoal**. Para distribuir o app, implementar **login por usuário, tokens individuais, limites de geração, rate limiting, prevenção de abuso e controle de gastos**, em vez de um token compartilhado.
- Defina limites de orçamento/uso na OpenAI Platform antes de testar. Geração de imagens e múltiplas referências podem gerar custo.
- Imagens do usuário são transmitidas ao servidor e à API da OpenAI ao solicitar geração com IA; deixe isso explícito na futura política de privacidade.
- O modo local não faz upload de imagens.
- Os exemplos de modelos contêm imagens fornecidas para este protótipo; confira direitos/licenças antes de redistribuir o aplicativo a terceiros.

## Testar o backend sem gastar créditos

```bash
cd server
python -m pip install -r requirements.txt pytest pillow
python -m pytest -q
```

O teste usa uma resposta simulada em memória, sem enviar requisições reais à OpenAI.

## Próximas melhorias recomendadas

1. Editor visual de posição e escala de camadas (arrastar/pinçar).
2. Ferramenta de recorte/remover fundo (com preservação de identidade).
3. Overlay de textos 100% determinístico **depois** da geração por IA, para títulos perfeitamente legíveis.
4. Salvar projetos, reabrir e editar; galeria de templates personalizada persistida no aparelho.
5. Backend multiusuário com autenticação individual e cotas; avaliar integração oficial SIWC quando disponível e adequada para o recurso de imagens.
