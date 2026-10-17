"""API privada para o Thumb Studio. Execute somente atrás de HTTPS e com autenticação."""
import base64
import hmac
import os
from io import BytesIO
from typing import Annotated

from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import Response
import httpx

app = FastAPI(title="Thumb Studio API", docs_url=None, redoc_url=None)
MAX_BYTES = 15 * 1024 * 1024
MAX_FILES = 4  # referência + até três imagens


def make_prompt(template: int, title: str, instruction: str, include_text: bool,
                left_label: str, right_label: str, source_count: int) -> str:
    styles = {
        1: """Composição split-screen vertical com divisão central. A imagem 2 é o lado ESQUERDO,
        imagem 3 é o lado DIREITO e imagem 4 é o PERSONAGEM central. Personagem atravessa a divisão.
        Conservar o estilo do personagem e contrastar o antes/depois sem trocar as imagens.""",
        2: """Personagem principal grande centralizado, mostrando ou segurando o objeto indicado.
        Imagem 2: personagem; imagem 3: objeto ou cenário. Texto grande atrás do personagem,
        hierarquia bem legível.""",
        3: """Personagem grande à ESQUERDA, capa do jogo à DIREITA e fundo do tema.
        Imagem 2: personagem; imagem 3: capa; imagem 4: fundo.
        Preservar grafismos e textos legíveis da capa sem reinterpretá-los.""",
        4: """Imagem 1 é o MODELO PERSONALIZADO a ser seguido em composição, posições,
        distribuição de elementos, paleta e hierarquia. Substituir os conteúdos do modelo
        com as demais imagens e dados do usuário. Não reutilizar personagens da referência
        quando o usuário forneceu personagens substitutos.""",
    }
    text_rule = (
        f"Texto obrigatório, exatamente como escrito (se fornecido): '{title}'. "
        + (f"Rótulos da divisão: esquerda '{left_label}', direita '{right_label}'. " if template == 1 else "")
        + "Não invente outros títulos, números ou logos." if include_text else
        "NÃO inclua nenhum texto novo ou sobreposto. Remova títulos do modelo de referência, mas preserve textos e logos que já fazem parte de capas e imagens fornecidas."
    )
    return f"""Gere UMA thumbnail para vídeo de YouTube em 16:9 (1536×864), profissional,
    alto impacto, legível em celular, sem bordas pretas adicionais. A imagem 1 é uma REFERÊNCIA
    ESTÉTICA/DE LAYOUT, não é fonte de conteúdo a copiar literalmente.
    {styles[template]}
    Recebidas {source_count} imagens de conteúdo após a referência, na ordem indicada.
    Prioridade máxima: conservar identidade visual de pessoas, personagens, poses, roupas e traços;
    não redesenhar elementos já presentes; evitar exagero de efeitos. Alterar só o estritamente
    necessário para adaptar o layout. Imagens com fundo transparente devem manter transparência
    nos recortes. Atenção: preservar personagens e capas é uma instrução, não uma garantia.
    {text_rule}
    Instruções adicionais do usuário: {instruction or 'Seguir modelo e insumos com acabamento limpo.'}
    """


async def load_image(image: UploadFile) -> tuple[str, BytesIO, str]:
    data = await image.read(MAX_BYTES + 1)
    if not data or len(data) > MAX_BYTES:
        raise HTTPException(413, "Arquivo vazio ou maior que 15 MB")
    # Aceita arquivos pelo cabeçalho real, independentemente do sufixo enviado pelo Android.
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        suffix, mime = ".png", "image/png"
    elif data.startswith(b"\xff\xd8\xff"):
        suffix, mime = ".jpg", "image/jpeg"
    elif data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        suffix, mime = ".webp", "image/webp"
    else:
        raise HTTPException(415, "Envie PNG, JPEG ou WebP")
    return "imagem" + suffix, BytesIO(data), mime


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/generate")
async def generate(
    template_id: Annotated[int, Form()],
    title: Annotated[str, Form()] = "",
    instruction: Annotated[str, Form()] = "",
    include_text: Annotated[bool, Form()] = True,
    left_label: Annotated[str, Form()] = "1999",
    right_label: Annotated[str, Form()] = "2026",
    model_reference: UploadFile = File(...),
    images: list[UploadFile] | None = File(default=None),
    x_app_token: Annotated[str | None, Header()] = None,
):
    secret = os.environ.get("APP_TOKEN", "")
    if not secret:
        raise HTTPException(503, "Servidor sem APP_TOKEN configurado")
    if not x_app_token or not hmac.compare_digest(secret, x_app_token):
        raise HTTPException(401, "Token inválido")
    if template_id not in (1, 2, 3, 4):
        raise HTTPException(422, "Modelo inexistente")
    images = images or []
    expected = {1: 3, 2: 2, 3: 3}
    if template_id in expected and len(images) != expected[template_id]:
        raise HTTPException(422, f"O modelo precisa de {expected[template_id]} imagens")
    if template_id == 4 and not (images or title.strip() or instruction.strip()):
        raise HTTPException(422, "Modelo personalizado precisa de imagens ou informações")
    if len(images) + 1 > MAX_FILES:
        raise HTTPException(422, "Limite de 4 imagens contando a referência")
    if len(title) > 160 or len(instruction) > 2500 or len(left_label) > 50 or len(right_label) > 50:
        raise HTTPException(422, "Texto muito longo")
    if not os.environ.get("OPENAI_API_KEY"):
        raise HTTPException(503, "OPENAI_API_KEY não configurada")
    uploaded = [await load_image(model_reference)]
    for img in images:
        uploaded.append(await load_image(img))
    prompt = make_prompt(template_id, title, instruction, include_text, left_label, right_label, len(images))
    # Multipart oficial: um campo image[] para cada arquivo de entrada.
    upload_parts = [("image[]", (f"fonte_{i}_{name}", file_obj, mime))
                    for i, (name, file_obj, mime) in enumerate(uploaded)]
    payload = {
        "model": os.environ.get("OPENAI_IMAGE_MODEL", "gpt-image-2"),
        "prompt": prompt,
        "size": "1536x864",  # 16:9 verdadeiro, compatível com GPT Image 2
        "quality": "medium",
        "output_format": "png",
    }
    try:
        with httpx.Client(timeout=220.0) as client:
            result = client.post(
                "https://api.openai.com/v1/images/edits",
                headers={"Authorization": f"Bearer {os.environ['OPENAI_API_KEY']}"},
                data=payload, files=upload_parts,
            )
        if result.status_code >= 400:
            raise HTTPException(502, f"Falha da OpenAI ({result.status_code}); confira chave, saldo, modelo e autorização")
        encoded = result.json()["data"][0].get("b64_json")
        if not encoded:
            raise HTTPException(502, "OpenAI não retornou dados da imagem")
        return Response(content=base64.b64decode(encoded), media_type="image/png",
                        headers={"Cache-Control": "no-store"})
    except (httpx.HTTPError, KeyError, ValueError, IndexError) as exc:
        raise HTTPException(502, "Falha de comunicação ou formato inesperado da OpenAI") from exc
