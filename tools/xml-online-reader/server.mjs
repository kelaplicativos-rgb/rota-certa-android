import http from 'node:http';

const PORT = Number(process.env.PORT || 8080);
const SOURCE_URL = String(
  process.env.ROTA_CERTA_XML_URL ||
  'https://rota-certa-7ccc8.web.app/v1/public/agenda-feed/ezequiel.xml'
).trim();

function writeText(res, status, body, extraHeaders = {}) {
  const text = String(body ?? '');
  res.writeHead(status, {
    'content-type': 'text/plain; charset=utf-8',
    'cache-control': 'no-store, max-age=0',
    'content-length': Buffer.byteLength(text),
    ...extraHeaders,
  });
  res.end(text);
}

function writeJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store, max-age=0',
    'content-length': Buffer.byteLength(body),
  });
  res.end(body);
}

async function fetchXml() {
  const response = await fetch(SOURCE_URL, {
    redirect: 'follow',
    headers: {
      'accept': 'application/xml,text/xml,text/plain,*/*',
      'user-agent': 'RotaCertaXmlReader/1.0',
    },
    signal: AbortSignal.timeout(15_000),
  });

  const body = await response.text();
  if (!response.ok) {
    throw new Error(`upstream HTTP ${response.status}: ${body.slice(0, 300)}`);
  }

  return {
    body,
    status: response.status,
    contentType: response.headers.get('content-type') || null,
    etag: response.headers.get('etag') || null,
    lastModified: response.headers.get('last-modified') || null,
  };
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || '/', 'http://localhost');

  if (req.method === 'GET' && url.pathname === '/healthz') {
    try {
      const xml = await fetchXml();
      return writeJson(res, 200, {
        ok: true,
        source_url: SOURCE_URL,
        upstream_status: xml.status,
        upstream_content_type: xml.contentType,
        bytes: Buffer.byteLength(xml.body),
        checked_at: new Date().toISOString(),
      });
    } catch (error) {
      return writeJson(res, 502, {
        ok: false,
        source_url: SOURCE_URL,
        error: String(error?.message || error),
        checked_at: new Date().toISOString(),
      });
    }
  }

  if (req.method === 'GET' && url.pathname === '/v1/public/agenda-feed/ezequiel.txt') {
    try {
      const xml = await fetchXml();
      return writeText(res, 200, xml.body, {
        'x-rota-certa-upstream-content-type': xml.contentType || 'unknown',
        'x-rota-certa-source': SOURCE_URL,
      });
    } catch (error) {
      return writeText(res, 502, `xml_proxy_error: ${String(error?.message || error)}`);
    }
  }

  if (req.method === 'GET' && url.pathname === '/v1/public/agenda-feed/ezequiel.json') {
    try {
      const xml = await fetchXml();
      return writeJson(res, 200, {
        ok: true,
        source_url: SOURCE_URL,
        fetched_at: new Date().toISOString(),
        upstream_content_type: xml.contentType,
        xml: xml.body,
      });
    } catch (error) {
      return writeJson(res, 502, {
        ok: false,
        source_url: SOURCE_URL,
        error: String(error?.message || error),
        fetched_at: new Date().toISOString(),
      });
    }
  }

  return writeJson(res, 404, { error: 'not_found' });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Rota Certa XML reader listening on ${PORT}`);
});
