package com.dualscan.zelleqr.sample;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.dualscan.zelleqr.ZelleQrLink;
import com.dualscan.zelleqr.ZelleQrLink.InvalidLinkException;
import com.dualscan.zelleqr.ZelleQrLink.Recipient;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

public final class MainActivity extends Activity {
    private static final String DEMO_LINK = "https://enroll.zellepay.com/qr-codes?data="
            + "eyJuYW1lIjoiVGF5bG9yIiwidG9rZW4iOiIxMjAyNTU1MDEyMyJ9";

    private final ZelleQrLink parser = new ZelleQrLink(BuildConfig.QR_LINK_HOST, true);
    private EditText url;
    private TextView error;
    private View result;
    private TextView name;
    private TextView token;
    private Button scan;
    private Button toggleToken;
    private Recipient recipient;
    private boolean tokenVisible;
    private int inputVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyInsets();
        url = findViewById(R.id.url);
        error = findViewById(R.id.error);
        result = findViewById(R.id.result);
        name = findViewById(R.id.recipient_name);
        token = findViewById(R.id.token);
        scan = findViewById(R.id.scan);
        toggleToken = findViewById(R.id.toggle_token);

        scan.setOnClickListener(view -> startScan());
        findViewById(R.id.decode).setOnClickListener(view -> decodePastedLink());
        findViewById(R.id.demo).setOnClickListener(view -> decodeLink(DEMO_LINK));
        findViewById(R.id.clear).setOnClickListener(view -> clear());
        toggleToken.setOnClickListener(view -> {
            tokenVisible = !tokenVisible;
            renderToken();
        });
        if (savedInstanceState == null) {
            handleIncomingIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            decodeLink(intent.getDataString());
        } else if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            decodeLink(shared == null ? null : shared.toString().trim());
        }
        setIntent(new Intent(Intent.ACTION_MAIN));
    }

    private void decodePastedLink() {
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        keyboard.hideSoftInputFromWindow(url.getWindowToken(), 0);
        url.clearFocus();
        decodeLink(url.getText().toString().trim());
    }

    private void decodeLink(String link) {
        inputVersion++;
        resetResult();
        try {
            recipient = parser.parse(link);
            name.setText(recipient.getName());
            renderToken();
            result.setVisibility(View.VISIBLE);
            url.setText("");
        } catch (InvalidLinkException exception) {
            int message = switch (exception.getReason()) {
                case INVALID_URL -> R.string.invalid_link;
                case UNSUPPORTED_LINK -> R.string.unsupported_link;
                case MISSING_DATA, DUPLICATE_DATA -> R.string.missing_data;
                case INVALID_BASE64, INVALID_PAYLOAD -> R.string.invalid_payload;
            };
            showError(message);
        }
    }

    private void startScan() {
        resetResult();
        int version = ++inputVersion;
        scan.setEnabled(false);
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build();
        GmsBarcodeScanning.getClient(this, options).startScan().addOnCompleteListener(task -> {
            if (isDestroyed() || isFinishing()) {
                return;
            }
            scan.setEnabled(true);
            if (version != inputVersion || task.isCanceled()) {
                return;
            }
            if (task.isSuccessful()) {
                decodeLink(task.getResult().getRawValue());
            } else {
                showError(R.string.scanner_unavailable);
            }
        });
    }

    private void renderToken() {
        if (recipient != null) {
            token.setText(tokenVisible ? recipient.getToken() : getString(R.string.token_hidden));
            toggleToken.setText(tokenVisible ? R.string.hide_token : R.string.show_token);
        }
    }

    private void clear() {
        inputVersion++;
        resetResult();
        url.setText("");
    }

    private void resetResult() {
        recipient = null;
        tokenVisible = false;
        name.setText("");
        token.setText("");
        error.setText("");
        error.setVisibility(View.GONE);
        result.setVisibility(View.GONE);
    }

    private void showError(int message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    private void applyInsets() {
        View scroll = findViewById(R.id.scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets safeArea = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom);
            }
            return insets;
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
    }
}
