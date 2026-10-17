"""Testes locais com OpenAI falsa: não gastam créditos."""
import base64
import io
import os
from types import SimpleNamespace

from fastapi.testclient import TestClient
from PIL import Image

import app as backend


def tiny_png():
    out = io.BytesIO()
    Image.new('RGB', (48, 27), (30, 50, 80)).save(out, format='PNG')
    return out.getvalue()


def test_templates_and_validation(monkeypatch):
    monkeypatch.setenv('APP_TOKEN', 'senha_teste')
    monkeypatch.setenv('OPENAI_API_KEY', 'sk-somente-mock')
    calls = []

    class MockResponse:
        status_code = 200
        def json(self):
            return {"data": [{"b64_json": base64.b64encode(tiny_png()).decode()}]}

    class MockClient:
        def __init__(self, **kwargs):
            pass
        def __enter__(self):
            return self
        def __exit__(self, *args):
            return False
        def post(self, url, **kwargs):
            calls.append(kwargs)
            return MockResponse()

    monkeypatch.setattr(backend.httpx, 'Client', MockClient)
    client = TestClient(backend.app)
    blob = tiny_png()
    base_files = [('model_reference', ('template.png', blob, 'image/png'))]
    files = base_files + [('images', (f'{n}.png', blob, 'image/png')) for n in range(3)]
    resp = client.post('/generate', headers={'X-App-Token': 'senha_teste'},
                       data={'template_id': '1', 'title': 'MELHOR JOGO', 'instruction': 'Azul suave',
                             'include_text': 'true', 'left_label': '1999', 'right_label': '2026'}, files=files)
    assert resp.status_code == 200, resp.text
    assert resp.headers['content-type'] == 'image/png'
    assert Image.open(io.BytesIO(resp.content)).size == (48, 27)
    assert calls[0]['data']['size'] == '1536x864'
    assert len(calls[0]['files']) == 4
    assert 'MELHOR JOGO' in calls[0]['data']['prompt']
    unauthorized = client.post('/generate', headers={'X-App-Token': 'errado'},
                               data={'template_id': '1'}, files=files)
    assert unauthorized.status_code == 401
    wrong_count = client.post('/generate', headers={'X-App-Token': 'senha_teste'},
                              data={'template_id': '2'}, files=files)
    assert wrong_count.status_code == 422
    custom = client.post('/generate', headers={'X-App-Token': 'senha_teste'},
                         data={'template_id': '4', 'instruction': 'Troque o fundo pela montanha', 'include_text': 'false'},
                         files=base_files)
    assert custom.status_code == 200, custom.text
    assert 'NÃO inclua nenhum texto novo' in calls[-1]['data']['prompt']
    assert len(calls) == 2
