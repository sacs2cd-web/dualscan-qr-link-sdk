import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { createZelleQRLink, getZelleQRDestinations, readZelleQRLink } from './zelle-qr-link.mjs';

const { payload, links } = JSON.parse(readFileSync(new URL('./fixtures/links.json', import.meta.url)));
const canonicalPrefix = 'https://enroll.zellepay.com/qr-codes?data=';
const payloadURL = (json) => canonicalPrefix + Buffer.from(json).toString('base64');

test('matches canonical and bridge QR fixtures', () => {
    assert.equal(createZelleQRLink(payload), links.canonical);
    assert.deepEqual(readZelleQRLink(links.canonical), payload);
    assert.equal(createZelleQRLink(payload, { bridgeHost: 'QR.EXAMPLE.COM' }), links['bridge-placeholder']);
    assert.deepEqual(readZelleQRLink(links['bridge-placeholder'], { bridgeHost: 'qr.example.com' }), payload);
    assert.throws(() => readZelleQRLink(links['bridge-placeholder']));
});

test('reuses the validated Base64 for fixed destinations', () => {
    const json = '{"name":"🚀","token":"токен","other":{"name":"nested"}}';
    const url = payloadURL(json);
    const { canonicalURL, sampleAppURL } = getZelleQRDestinations(url);
    assert.equal(canonicalURL, canonicalPrefix + encodeURIComponent(url.slice(canonicalPrefix.length)));
    assert.deepEqual(readZelleQRLink(sampleAppURL), { name: '🚀', token: 'токен' });
});

test('accepts padded and unpadded Base64URL, a literal plus, and trailing slash', () => {
    const json = JSON.stringify({ name: '🚀', token: 'synthetic-opaque-token-1' });
    const base64 = Buffer.from(json).toString('base64');
    const base64URL = base64.replace(/\+/g, '-').replace(/\//g, '_');
    assert.match(base64, /[+\/]/);
    assert.match(base64, /=/);
    for (const data of [base64, encodeURIComponent(base64), base64URL, base64URL.replace(/=+$/, '')]) {
        assert.deepEqual(
            readZelleQRLink(`HTTPS://ENROLL.ZELLEPAY.COM/qr-codes/?other=yes&%64ata=${data}`),
            JSON.parse(json)
        );
    }
});

test('rejects alternate authorities, normalized paths, and ambiguous query data', () => {
    const data = links.canonical.split('?data=')[1];
    for (const link of [
        `http://enroll.zellepay.com/qr-codes?data=${data}`,
        `https://enroll.zellepay.com:443/qr-codes?data=${data}`,
        `https://user@enroll.zellepay.com/qr-codes?data=${data}`,
        `https://enroll.zellepay.com.evil.test/qr-codes?data=${data}`,
        `https://enroll.zellepay.com/other/../qr-codes?data=${data}`,
        `https://enroll.zellepay.com/%71r-codes?data=${data}`,
        `https://enroll.zellepay.com/QR-CODES?data=${data}`,
        links.canonical + '#example',
        links.canonical + '&%64ata=' + data,
        links.canonical + '&bad=%FF',
        links.canonical + '&raw=é',
        links.canonical + '%0A',
        canonicalPrefix,
        canonicalPrefix + 'A'.repeat(4097),
        links.canonical + '&x=' + 'a'.repeat(8192)
    ]) {
        assert.throws(() => readZelleQRLink(link), link.slice(0, 200));
    }
});

test('rejects malformed payloads and duplicate recipient fields', () => {
    for (const json of [
        '{"name":"Taylor","token":123}',
        '{"name":"Taylor","token":" "}',
        '{"name":"Taylor","token":"\\uD800"}',
        '{"name":"Taylor"}',
        '[{"name":"Taylor","token":"recipient"}]',
        '{"name":"Taylor","name":"Other","token":"recipient"}',
        '{"name":"Taylor","token":"recipient","to\\u006ben":"other"}',
        '{"name":"Taylor","token":"recipient"} trailing',
        '\uFEFF{"name":"Taylor","token":"recipient"}'
    ]) {
        assert.throws(() => readZelleQRLink(payloadURL(json)), json);
    }
    const invalidUTF8 = Buffer.concat([Buffer.from('{"name":"'), Buffer.of(0xff), Buffer.from('","token":"T"}')]);
    assert.throws(() => readZelleQRLink(canonicalPrefix + invalidUTF8.toString('base64')));
    const dataWithTailBits = Buffer.from('{"name":"A","token":"TT"}').toString('base64');
    assert.match(dataWithTailBits, /Q==$/);
    assert.throws(() => readZelleQRLink(canonicalPrefix + dataWithTailBits.replace(/Q==$/, 'R==')));
});
