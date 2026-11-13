import { createZelleQRLink, getZelleQRDestinations } from '../zelle-qr-link.mjs';

const status = document.querySelector('#status');
const openSample = document.querySelector('#open-sample');
const continueLink = document.querySelector('#continue');

try {
    const link = window.location.href;
    const { canonicalURL, sampleAppURL } = getZelleQRDestinations(
        link, { bridgeHost: window.location.hostname }
    );
    const demo = getZelleQRDestinations(createZelleQRLink({ name: 'Taylor', token: '12025550123' }));
    const isDemo = canonicalURL === demo.canonicalURL;
    status.textContent = isDemo
        ? 'Open the installed sample app to read the test recipient.'
        : 'Continue to the original QR link.';
    if (isDemo) {
        openSample.href = sampleAppURL;
        openSample.hidden = false;
    }
    continueLink.href = canonicalURL;
    continueLink.hidden = false;
} catch {
    status.textContent = 'This QR link could not be read. Scan the original QR code again.';
}
