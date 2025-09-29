import { createServer as createHttpServer } from 'node:http';
import { parseJson } from '../util/json.js';
import { createApp } from './app.js';

/**
 * The node adapter.
 *
 * Everything above this file is pure: a request is an object and a response is
 * an object, which is why the whole service can be tested without a socket.
 * This is the only place that knows about streams, headers and ports, and it
 * is deliberately thin so there is nothing here worth testing twice.
 */

const MAX_BODY_BYTES = 1024 * 1024;

/** Read a request body, refusing anything larger than the cap. */
export function readBody(request, { limit = MAX_BODY_BYTES } = {}) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    request.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(new Error('request body is too large'));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    request.on('error', reject);
  });
}

/** Split a url into its path and its query, with no dependency on a base url. */
export function splitUrl(url) {
  const at = url.indexOf('?');
  if (at === -1) return { path: url, query: {} };
  const query = {};
  for (const pair of url.slice(at + 1).split('&')) {
    if (pair === '') continue;
    const equals = pair.indexOf('=');
    const key = equals === -1 ? pair : pair.slice(0, equals);
    const value = equals === -1 ? '' : pair.slice(equals + 1);
    query[decodeURIComponent(key)] = decodeURIComponent(value.replace(/\+/g, ' '));
  }
  return { path: url.slice(0, at), query };
}

/** Build a listening server over an app. */
export function createServer(app = createApp()) {
  const server = createHttpServer((request, response) => {
    handleRequest(app, request, response).catch((err) => {
      response.writeHead(500, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ error: 'unhandled', message: String(err.message ?? err) }));
    });
  });
  return { server, app };
}

async function handleRequest(app, request, response) {
  const { path, query } = splitUrl(request.url ?? '/');
  const raw = await readBody(request);
  let body = null;
  if (raw !== '') {
    try {
      body = parseJson(raw, 'body');
    } catch (err) {
      response.writeHead(400, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ error: err.message, code: err.code }));
      return;
    }
  }
  const result = await app.request({ method: request.method ?? 'GET', path, query, body });
  const payload = result.body === null ? '' : JSON.stringify(result.body);
  response.writeHead(result.status, {
    'content-type': 'application/json',
    'content-length': Buffer.byteLength(payload),
  });
  response.end(payload);
}

/** Start listening, resolving once the port is bound. */
export function listen({ server }, port = 0) {
  return new Promise((resolve) => {
    server.listen(port, () => resolve(server.address().port));
  });
}

/** Stop listening, resolving once every connection has closed. */
export function close({ server }) {
  return new Promise((resolve, reject) => {
    server.close((err) => (err ? reject(err) : resolve()));
  });
}
