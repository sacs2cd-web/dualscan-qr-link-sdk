import qrcode from './vendor/qrcode-generator.mjs';
import { createZelleQRLink, getZelleQRDestinations } from './zelle-qr-link.mjs';

const sampleRecipient = { name: 'Taylor', token: '12025550123' };
const { sampleAppURL } = getZelleQRDestinations(createZelleQRLink(sampleRecipient));

const form = document.querySelector('#generator');
const destination = document.querySelector('#destination');
const bridgeHost = document.querySelector('#bridge-host');
const bridgeField = document.querySelector('#bridge-field');
const name = document.querySelector('#recipient-name');
const token = document.querySelector('#recipient-token');
const preview = document.querySelector('#preview');
const qrImage = document.querySelector('#qr');
const generatedLink = document.querySelector('#generated-link');
const status = document.querySelector('#status');
const sampleActions = document.querySelector('#sample-actions');
document.querySelector('#open-sample').href = sampleAppURL;

function updateBridgeField() {
    const useBridge = destination.value === 'bridge';
    bridgeField.hidden = !useBridge;
    bridgeHost.disabled = !useBridge;
    bridgeHost.required = useBridge;
}

function renderQR() {
    try {
        const recipient = { name: name.value.trim(), token: token.value.trim() };
        const url = createZelleQRLink(
            recipient,
            destination.value === 'bridge' ? { bridgeHost: bridgeHost.value.trim() } : {}
        );
        const qr = qrcode(0, 'M');
        qr.addData(url, 'Byte');
        qr.make();
        qrImage.src = qr.createDataURL(8, 32);
        generatedLink.value = url;
        status.hidden = true;
        preview.hidden = false;
        sampleActions.hidden = recipient.name !== sampleRecipient.name || recipient.token !== sampleRecipient.token;
    } catch (error) {
        preview.hidden = true;
        sampleActions.hidden = true;
        status.textContent = error instanceof Error ? error.message : 'The data does not fit in a QR code.';
        status.hidden = false;
    }
}

destination.addEventListener('change', updateBridgeField);
form.addEventListener('submit', (event) => {
    event.preventDefault();
    renderQR();
});

updateBridgeField();
renderQR();
