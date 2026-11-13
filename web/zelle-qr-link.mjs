const CANONICAL_HOST = 'enroll.zellepay.com';
const APP_SCHEME = 'dualscan-zelleqr';
const APP_AUTHORITY = 'qr';
const PATH = '/qr-codes';
const URL_LIMIT = 8192;
const DATA_LIMIT = 4096;

const encoder = new TextEncoder();
const decoder = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true });
const hostnamePattern = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*$/i;
const rawLinkPattern = /^([a-z][a-z0-9+.-]*):\/\/([^/?#]+)(\/qr-codes\/?)\?([^#]*)$/i;
const blankField = /^[\u0009-\u000D\u001C-\u0020\u0085\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]*$/u;
const loneSurrogate = /[\uD800-\uDFFF]/u;

function normalizeBridgeHost(host) {
    if (typeof host !== 'string' || host.length > 253 || !hostnamePattern.test(host) || /[^\x21-\x7E]/.test(host)) {
        throw new Error('Enter a host name such as qr.example.com.');
    }
    return host.toLowerCase();
}

function requireFields(payload) {
    const { name, token } = payload ?? {};
    if ([name, token].some((value) => typeof value !== 'string' || blankField.test(value) || loneSurrogate.test(value))) {
        throw new Error('The QR data needs a name and a recipient token.');
    }
    return { name, token };
}

function extractData(query) {
    let data;
    for (const parameter of query.split('&')) {
        const separator = parameter.indexOf('=');
        const key = separator < 0 ? parameter : parameter.slice(0, separator);
        const value = separator < 0 ? '' : decodeURIComponent(parameter.slice(separator + 1));
        if (decodeURIComponent(key) !== 'data') continue;
        if (data !== undefined) throw new Error('The link contains multiple data values.');
        data = value;
    }
    if (!data || data.length > DATA_LIMIT) throw new Error('The link needs a valid data value.');
    return data;
}

function rejectDuplicateFields(json) {
    const seen = new Set();
    let depth = 0;
    for (const match of json.matchAll(/"(?:[^"\\]|\\[\s\S])*"|[{}[\]]/g)) {
        const [token] = match;
        if (token === '{' || token === '[') depth += 1;
        if (token === '}' || token === ']') depth -= 1;
        if (depth !== 1 || token[0] !== '"' ||
            !json.slice(match.index + token.length).trimStart().startsWith(':')) continue;
        const key = JSON.parse(token);
        if (key !== 'name' && key !== 'token') continue;
        if (seen.has(key)) throw new Error('The QR data repeats a recipient field.');
        seen.add(key);
    }
}

function decodePayload(data) {
    const match = /^([a-z0-9+/_-]+)(={0,2})$/i.exec(data);
    if (!match || match[0].length !== data.length || (/[+/]/.test(data) && /[_-]/.test(data))) {
        throw new Error('The link contains invalid Base64 data.');
    }

    const [, unpadded, padding] = match;
    const missingPadding = (4 - unpadded.length % 4) % 4;
    if (missingPadding === 3 || (padding && padding.length !== missingPadding)) {
        throw new Error('The link contains invalid Base64 padding.');
    }

    const normalized = unpadded.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat(missingPadding);
    const binary = atob(normalized);
    if (btoa(binary) !== normalized) throw new Error('The link contains invalid Base64 data.');
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    const json = decoder.decode(bytes);
    const payload = requireFields(JSON.parse(json));
    rejectDuplicateFields(json);
    return payload;
}

function parseLink(link, bridgeHost) {
    if (typeof link !== 'string' || link.length > URL_LIMIT || /[^\x21-\x7E]/.test(link)) {
        throw new Error('The QR link is invalid.');
    }

    const match = rawLinkPattern.exec(link);
    if (!match || (match[3] !== PATH && match[3] !== PATH + '/')) {
        throw new Error('The link does not use the QR code path.');
    }
    const [, rawScheme, rawAuthority, , query] = match;
    const scheme = rawScheme.toLowerCase();
    const authority = rawAuthority.toLowerCase();
    const isHTTPS = scheme === 'https' && (
        authority === CANONICAL_HOST ||
        (bridgeHost !== undefined && authority === normalizeBridgeHost(bridgeHost))
    );
    const isCustom = scheme === APP_SCHEME && authority === APP_AUTHORITY;
    if (!isHTTPS && !isCustom) throw new Error('The link host or scheme is not supported.');

    const data = extractData(query);
    return { data, payload: decodePayload(data) };
}

function encodePayload(payload) {
    const fields = requireFields(payload);
    if (fields.name.length + fields.token.length > DATA_LIMIT) {
        throw new Error('The QR data is too long.');
    }
    const bytes = encoder.encode(JSON.stringify(fields));
    const encoded = btoa(String.fromCharCode(...bytes));
    if (encoded.length > DATA_LIMIT) throw new Error('The QR data is too long.');
    return encoded;
}

/** Builds a canonical HTTPS QR link or an HTTPS QR link on the configured bridge host. */
export function createZelleQRLink(payload, { bridgeHost } = {}) {
    const host = bridgeHost === undefined ? CANONICAL_HOST : normalizeBridgeHost(bridgeHost);
    const link = `https://${host}${PATH}?data=${encodeURIComponent(encodePayload(payload))}`;
    if (link.length > URL_LIMIT) throw new Error('The QR link is too long.');
    return link;
}

/** Reads the name and opaque token in a QR link. Throws when the link is invalid. */
export function readZelleQRLink(link, { bridgeHost } = {}) {
    return parseLink(link, bridgeHost).payload;
}

/** Carries validated QR data to fixed destinations while preserving the encoded payload. */
export function getZelleQRDestinations(link, { bridgeHost } = {}) {
    const { data, payload } = parseLink(link, bridgeHost);
    const query = `?data=${encodeURIComponent(data)}`;
    const canonicalURL = `https://${CANONICAL_HOST}${PATH}${query}`;
    if (canonicalURL.length > URL_LIMIT) throw new Error('The QR link is too long.');
    return { payload, canonicalURL, sampleAppURL: `${APP_SCHEME}://${APP_AUTHORITY}${PATH}${query}` };
}
