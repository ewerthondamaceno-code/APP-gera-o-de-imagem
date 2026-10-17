package br.com.angel.thumbstudio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int BG = 0xFF071423;
    private static final int PANEL = 0xFF112337;
    private static final int LIGHT = 0xFFDFEAF5;
    private static final int MUTED = 0xFF9BAFC0;
    private static final int ACCENT = 0xFF23D1F4;
    private static final int PICK_BASE = 110;
    private static final int PICK_REFERENCE = 160;
    private static final int SAVE_PNG = 200;

    private int template = 1;
    private final ArrayList<Uri> chosen = new ArrayList<>();
    private Uri referenceUri;
    private final ArrayList<ImageView> slotImages = new ArrayList<>();
    private final ArrayList<TextView> slotStatuses = new ArrayList<>();
    private final ArrayList<LinearLayout> templateCards = new ArrayList<>();
    private LinearLayout slotsView;
    private LinearLayout customRefSection;
    private LinearLayout splitExtras;
    private ImageView referenceImage;
    private TextView referenceStatus;
    private TextView previewStatus;
    private ImageView preview;
    private EditText titleEdit, instructionEdit, leftEdit, rightEdit, urlEdit, tokenEdit;
    private Switch textToggle;
    private Button localButton, aiButton, saveButton;
    private ProgressBar spinner;
    private Bitmap currentResult;
    private boolean busy;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable previewTask;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        for (int i = 0; i < 3; i++) chosen.add(null);
        setContentView(createScreen());
        selectTemplate(1);
    }

    private View createScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(25), dp(18), dp(32));
        scroll.addView(page);

        TextView brand = text("THUMB STUDIO", 27, Color.WHITE, true);
        page.addView(brand);
        TextView sub = text("Crie thumbnails com seus próprios modelos • 16:9", 13, MUTED, false);
        margins(page, sub, 0, 1, 0, 18);

        heading(page, "1. Escolha um modelo");
        HorizontalScrollView modelScroll = new HorizontalScrollView(this);
        modelScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout gallery = new LinearLayout(this);
        gallery.setOrientation(LinearLayout.HORIZONTAL);
        modelScroll.addView(gallery);
        page.addView(modelScroll);
        String[] names = {"01 · SPLIT-SCREEN", "02 · PERSONAGEM", "03 · CAPA + PERSONAGEM", "04 · PERSONALIZADO"};
        for (int t = 1; t <= 4; t++) {
            final int id = t;
            LinearLayout card = vertical();
            card.setPadding(dp(4), dp(4), dp(4), dp(7));
            card.setBackground(round(PANEL, 12, 0xFF24425A, 1));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(176), dp(133));
            cardParams.setMargins(0, 0, dp(10), 0);
            gallery.addView(card, cardParams);
            if (t < 4) {
                ImageView example = new ImageView(this);
                example.setScaleType(ImageView.ScaleType.CENTER_CROP);
                try (InputStream stream = getAssets().open("modelo_" + t + ".png")) {
                    example.setImageBitmap(BitmapFactory.decodeStream(stream));
                } catch (IOException ignored) { }
                card.addView(example, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(94)));
            } else {
                TextView customIcon = text("+", 44, ACCENT, true);
                customIcon.setGravity(Gravity.CENTER);
                customIcon.setBackground(round(0xFF142F43, 8, 0, 0));
                card.addView(customIcon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(94)));
            }
            TextView label = text(names[t - 1], 11, LIGHT, true);
            label.setGravity(Gravity.CENTER);
            margins(card, label, 0, 8, 0, 0);
            card.setOnClickListener(v -> selectTemplate(id));
            templateCards.add(card);
        }

        heading(page, "2. Imagens e referência");
        TextView hint = text("Use PNG com fundo transparente para personagens recortados. A montagem sem IA mantém os pixels originais.", 12, MUTED, false);
        margins(page, hint, 0, 0, 0, 10);
        customRefSection = vertical();
        page.addView(customRefSection);
        TextView cTitle = text("Modelo que deseja reproduzir", 13, LIGHT, true);
        margins(customRefSection, cTitle, 0, 0, 0, 6);
        LinearLayout refRow = horizontal();
        customRefSection.addView(refRow);
        referenceImage = new ImageView(this);
        referenceImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        referenceImage.setBackground(round(0xFF24405B, 8, 0, 0));
        refRow.addView(referenceImage, new LinearLayout.LayoutParams(dp(100), dp(62)));
        LinearLayout refRight = vertical();
        LinearLayout.LayoutParams rParam = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        rParam.setMargins(dp(12), 0, 0, 0);
        refRow.addView(refRight, rParam);
        Button refButton = action("Escolher referência", true);
        refRight.addView(refButton);
        refButton.setOnClickListener(v -> openPicker(PICK_REFERENCE));
        referenceStatus = text("Nenhuma referência selecionada", 11, MUTED, false);
        margins(refRight, referenceStatus, 0, 3, 0, 0);
        slotsView = vertical();
        margins(page, slotsView, 0, 7, 0, 0);

        heading(page, "3. Descreva a imagem");
        TextView instructionsHint = text("Explique o tema, estilo ou quais elementos devem ser substituídos.", 12, MUTED, false);
        margins(page, instructionsHint, 0, 0, 0, 7);
        instructionEdit = edit("Ex.: destaque o personagem, fundo de terror escuro, iluminação realista...", true);
        instructionEdit.setMinLines(3);
        page.addView(instructionEdit);

        LinearLayout textLine = horizontal();
        textLine.setGravity(Gravity.CENTER_VERTICAL);
        margins(page, textLine, 0, 16, 0, 6);
        TextView addText = text("Adicionar texto à imagem", 15, LIGHT, true);
        textLine.addView(addText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        textToggle = new Switch(this);
        textToggle.setChecked(true);
        textLine.addView(textToggle);
        titleEdit = edit("Título da thumbnail", false);
        page.addView(titleEdit);
        splitExtras = horizontal();
        margins(page, splitExtras, 0, 7, 0, 0);
        leftEdit = edit("Texto da esquerda (ex.: 1999)", false);
        rightEdit = edit("Texto da direita (ex.: 2026)", false);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l.setMargins(0, 0, dp(6), 0);
        splitExtras.addView(leftEdit, l);
        splitExtras.addView(rightEdit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        textToggle.setOnCheckedChangeListener((b, checked) -> {
            titleEdit.setVisibility(checked ? View.VISIBLE : View.GONE);
            splitExtras.setVisibility(checked && template == 1 ? View.VISIBLE : View.GONE);
            schedulePreview();
        });

        heading(page, "4. Prévia e exportação");
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(round(PANEL, 12, 0, 0));
        page.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(205)));
        previewStatus = text("Exemplo original do modelo", 12, MUTED, false);
        margins(page, previewStatus, 0, 5, 0, 12);

        localButton = action("Montar sem IA • preservar imagens", false);
        page.addView(localButton, match());
        localButton.setOnClickListener(v -> renderLocally());
        TextView aiHead = text("GERAÇÃO POR INTELIGÊNCIA ARTIFICIAL", 12, ACCENT, true);
        margins(page, aiHead, 0, 23, 0, 9);
        TextView info = text("A IA tenta seguir o modelo e suas instruções, mas pode modificar rostos, traços e detalhes. Para preservação exata, prefira a montagem sem IA.", 12, MUTED, false);
        margins(page, info, 0, 0, 0, 8);
        urlEdit = edit("https://endereco-do-seu-servidor", false);
        urlEdit.setText(getPreferences(MODE_PRIVATE).getString("server_url", ""));
        page.addView(urlEdit);
        TextView urlHint = text("URL HTTPS do servidor de geração (chave da OpenAI fica no servidor)", 11, MUTED, false);
        margins(page, urlHint, 0, 4, 0, 10);
        tokenEdit = edit("Token privado do seu servidor", false);
        tokenEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        page.addView(tokenEdit);
        TextView tokenHint = text("Token não é a senha nem a chave da sua conta ChatGPT. Por segurança, ele não fica salvo no app.", 11, MUTED, false);
        margins(page, tokenHint, 0, 4, 0, 12);
        aiButton = action("Gerar com IA", true);
        page.addView(aiButton, match());
        aiButton.setOnClickListener(v -> generateWithAi());
        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        spinnerLp.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerLp.setMargins(0, dp(8), 0, 0);
        page.addView(spinner, spinnerLp);

        saveButton = action("Salvar resultado PNG • 16:9", false);
        margins(page, saveButton, 0, 11, 0, 0);
        saveButton.setEnabled(false);
        saveButton.setAlpha(.5f);
        saveButton.setOnClickListener(v -> chooseSave());
        TextView footer = text("Modo local: 1280×720. Com IA: 1536×864. Ambos em 16:9.\nImagens originais dos modelos incluídas com autorização do autor.", 11, MUTED, false);
        margins(page, footer, 0, 13, 0, 0);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { schedulePreview(); }
            @Override public void afterTextChanged(Editable s) { }
        };
        titleEdit.addTextChangedListener(watcher);
        leftEdit.addTextChangedListener(watcher);
        rightEdit.addTextChangedListener(watcher);
        return scroll;
    }

    private void selectTemplate(int newTemplate) {
        template = newTemplate;
        chosen.clear();
        for (int i = 0; i < 3; i++) chosen.add(null);
        currentResult = null;
        enableSave(false);
        for (int i = 0; i < templateCards.size(); i++) {
            boolean selected = i == template - 1;
            templateCards.get(i).setBackground(round(selected ? 0xFF12364D : PANEL,
                    12, selected ? ACCENT : 0xFF24425A, selected ? 2 : 1));
        }
        customRefSection.setVisibility(template == 4 ? View.VISIBLE : View.GONE);
        localButton.setVisibility(template == 4 ? View.GONE : View.VISIBLE);
        splitExtras.setVisibility(textToggle.isChecked() && template == 1 ? View.VISIBLE : View.GONE);
        if (template == 1) { titleEdit.setText(""); leftEdit.setText("1999"); rightEdit.setText("2026"); }
        else if (template == 2) titleEdit.setText("PC FRACO");
        else if (template == 3) titleEdit.setText("ATÉ ZERAR");
        else titleEdit.setText("");
        rebuildSlots();
        showExample();
    }

    private void rebuildSlots() {
        slotsView.removeAllViews();
        slotImages.clear();
        slotStatuses.clear();
        String[] names;
        if (template == 1) names = new String[]{"Cenário esquerdo", "Cenário direito", "Personagem central"};
        else if (template == 2) names = new String[]{"Personagem principal", "Objeto ou plano de fundo"};
        else if (template == 3) names = new String[]{"Personagem à esquerda", "Capa do jogo à direita", "Plano de fundo"};
        else names = new String[]{"Imagem para substituir (opcional)", "Imagem adicional (opcional)", "Imagem adicional (opcional)"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout line = horizontal();
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(9), dp(8), dp(9), dp(8));
            line.setBackground(round(PANEL, 10, 0xFF2B4054, 1));
            margins(slotsView, line, 0, 0, 0, 8);
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackground(round(0xFF294358, 7, 0, 0));
            line.addView(img, new LinearLayout.LayoutParams(dp(64), dp(58)));
            slotImages.add(img);
            LinearLayout labels = vertical();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(dp(10), 0, dp(8), 0);
            line.addView(labels, lp);
            labels.addView(text((i + 1) + ". " + names[i], 12, LIGHT, true));
            TextView state = text("Aguardando imagem", 11, MUTED, false);
            margins(labels, state, 0, 5, 0, 0);
            slotStatuses.add(state);
            Button picker = action("Escolher", true);
            picker.setTextSize(11);
            line.addView(picker, new LinearLayout.LayoutParams(dp(88), dp(43)));
            picker.setOnClickListener(v -> openPicker(PICK_BASE + index));
        }
    }

    private void openPicker(int requestCode) {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == SAVE_PNG) {
            if (currentResult == null) return;
            try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                if (output == null || !currentResult.compress(Bitmap.CompressFormat.PNG, 100, output))
                    throw new IOException("Falha ao escrever imagem");
                notice("Imagem PNG salva com sucesso!");
            } catch (IOException e) { notice("Não foi possível salvar: " + e.getMessage()); }
            return;
        }
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        if (requestCode == PICK_REFERENCE) {
            referenceUri = uri;
            referenceImage.setImageURI(uri);
            referenceStatus.setText("Referência selecionada");
        } else if (requestCode >= PICK_BASE && requestCode < PICK_BASE + chosen.size()) {
            int index = requestCode - PICK_BASE;
            chosen.set(index, uri);
            slotStatuses.get(index).setText("Imagem selecionada ✓");
            slotStatuses.get(index).setTextColor(ACCENT);
            try {
                Bitmap bitmap = ThumbnailRenderer.readBitmap(this, uri);
                slotImages.get(index).setImageBitmap(bitmap);
            } catch (Exception e) { notice("Erro ao abrir imagem: " + e.getMessage()); }
        }
        currentResult = null;
        enableSave(false);
        schedulePreview();
    }

    private void schedulePreview() {
        if (previewTask != null) ui.removeCallbacks(previewTask);
        previewTask = this::refreshPreview;
        ui.postDelayed(previewTask, 350);
    }

    private void refreshPreview() {
        if (busy) return;
        if (template == 4) {
            if (referenceUri == null) showExample();
            else {
                try {
                    preview.setImageBitmap(ThumbnailRenderer.readBitmap(this, referenceUri));
                    previewStatus.setText("Modelo personalizado • imagem de referência (a substituição exige IA)");
                } catch (IOException e) { showExample(); }
            }
            return;
        }
        List<Uri> images = requiredImages(false);
        if (images == null) { showExample(); return; }
        try {
            Bitmap value = ThumbnailRenderer.render(this, template, images, textToggle.isChecked(),
                    titleEdit.getText().toString(), leftEdit.getText().toString(), rightEdit.getText().toString());
            preview.setImageBitmap(value);
            previewStatus.setText("Prévia local 16:9 com suas imagens • sem geração de IA");
        } catch (Exception error) {
            previewStatus.setText("Falha na prévia: " + error.getMessage());
        }
    }

    private void showExample() {
        if (template == 4) {
            preview.setImageDrawable(null);
            previewStatus.setText("Escolha uma imagem de referência acima");
            return;
        }
        try (InputStream stream = getAssets().open("modelo_" + template + ".png")) {
            preview.setImageBitmap(BitmapFactory.decodeStream(stream));
            previewStatus.setText("Exemplo original enviado pelo criador • envie todas as imagens para ver sua prévia");
        } catch (IOException e) { previewStatus.setText("Falha ao abrir exemplo"); }
    }

    private List<Uri> requiredImages(boolean withMessage) {
        int total = template == 2 ? 2 : 3;
        ArrayList<Uri> images = new ArrayList<>();
        if (template == 4) {
            for (Uri uri : chosen) if (uri != null) images.add(uri);
            return images;
        }
        for (int i = 0; i < total; i++) {
            if (chosen.get(i) == null) {
                if (withMessage) notice("Selecione as " + total + " imagens do modelo antes de continuar.");
                return null;
            }
            images.add(chosen.get(i));
        }
        return images;
    }

    private void renderLocally() {
        List<Uri> files = requiredImages(true);
        if (files == null) return;
        try {
            currentResult = ThumbnailRenderer.render(this, template, files, textToggle.isChecked(),
                    titleEdit.getText().toString(), leftEdit.getText().toString(), rightEdit.getText().toString());
            preview.setImageBitmap(currentResult);
            previewStatus.setText("Montagem pronta: 1280×720 • arquivos originais preservados");
            enableSave(true);
            notice("Montagem concluída! Você já pode salvar o PNG.");
        } catch (Exception e) { notice("Erro na montagem: " + e.getMessage()); }
    }

    private void generateWithAi() {
        List<Uri> files = requiredImages(true);
        if (files == null) return;
        if (template == 4 && referenceUri == null) { notice("Selecione uma imagem de modelo personalizado."); return; }
        if (template == 4 && files.isEmpty() && instructionEdit.getText().toString().trim().isEmpty() && titleEdit.getText().toString().trim().isEmpty()) {
            notice("Informe as imagens ou um texto para substituir na referência."); return;
        }
        final String url = urlEdit.getText().toString().trim();
        final String token = tokenEdit.getText().toString();
        if (url.isEmpty() || token.trim().isEmpty()) {
            notice("Preencha o endereço HTTPS e o token de seu servidor."); return;
        }
        getPreferences(MODE_PRIVATE).edit().putString("server_url", url).apply();
        final int model = template;
        final Uri custom = referenceUri;
        final String title = titleEdit.getText().toString();
        final String note = instructionEdit.getText().toString();
        final boolean withText = textToggle.isChecked();
        final String left = leftEdit.getText().toString();
        final String right = rightEdit.getText().toString();
        setBusy(true);
        previewStatus.setText("Gerando com OpenAI... a solicitação pode levar alguns minutos.");
        new Thread(() -> {
            try {
                Bitmap generated = OpenAiGateway.generate(getApplicationContext(), url, token, model,
                        files, custom, title, note, withText, left, right);
                runOnUiThread(() -> {
                    currentResult = generated;
                    preview.setImageBitmap(generated);
                    previewStatus.setText("Imagem criada pela IA • 1536×864 • PNG 16:9");
                    enableSave(true);
                    setBusy(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    previewStatus.setText("Falha na geração: " + e.getMessage());
                    notice("Erro ao gerar. Confira os dados do servidor.");
                });
            }
        }, "ThumbStudio-AI").start();
    }

    private void setBusy(boolean value) {
        busy = value;
        aiButton.setEnabled(!value);
        localButton.setEnabled(!value);
        spinner.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    private void enableSave(boolean enable) {
        if (saveButton == null) return;
        saveButton.setEnabled(enable);
        saveButton.setAlpha(enable ? 1.0f : 0.5f);
    }

    private void chooseSave() {
        if (currentResult == null) { notice("Gere uma imagem antes de salvar."); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "thumb-studio-" + System.currentTimeMillis() + ".png");
        startActivityForResult(intent, SAVE_PNG);
    }

    private void notice(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int dp(float units) { return Math.round(units * getResources().getDisplayMetrics().density); }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }

    private GradientDrawable round(int fill, float radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), stroke);
        return d;
    }

    private TextView text(String content, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return view;
    }

    private void heading(LinearLayout parent, String text) {
        TextView label = text(text, 18, LIGHT, true);
        margins(parent, label, 0, 20, 0, 12);
    }

    private EditText edit(String hint, boolean multiline) {
        EditText field = new EditText(this);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(0xFF8095A8);
        field.setHint(hint);
        field.setTextSize(14);
        field.setPadding(dp(13), dp(12), dp(13), dp(12));
        field.setBackground(round(PANEL, 9, 0xFF29465E, 1));
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        if (!multiline) field.setSingleLine(true);
        return field;
    }

    private Button action(String label, boolean outline) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setTextColor(outline ? ACCENT : 0xFF071423);
        button.setBackground(round(outline ? PANEL : ACCENT, 10, outline ? ACCENT : ACCENT, 1));
        button.setMinHeight(dp(49));
        return button;
    }

    private void margins(LinearLayout parent, View item, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = match();
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        parent.addView(item, params);
    }
}
