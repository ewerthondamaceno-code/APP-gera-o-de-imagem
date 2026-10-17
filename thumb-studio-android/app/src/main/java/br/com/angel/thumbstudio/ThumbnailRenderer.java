package br.com.angel.thumbstudio;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/** Montagem por camadas: mantém os pixels dos arquivos enviados, sem regeneração por IA. */
public final class ThumbnailRenderer {
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;
    private static final int CYAN = Color.rgb(40, 210, 246);

    private ThumbnailRenderer() { }

    public static Bitmap readBitmap(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        BitmapFactory.Options info = new BitmapFactory.Options();
        info.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) throw new IOException("Arquivo não encontrado");
            BitmapFactory.decodeStream(stream, null, info);
        }
        if (info.outWidth <= 0 || info.outHeight <= 0) throw new IOException("Imagem inválida");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(info.outWidth / options.inSampleSize, info.outHeight / options.inSampleSize) > 2304) {
            options.inSampleSize *= 2;
        }
        try (InputStream stream = resolver.openInputStream(uri)) {
            Bitmap result = BitmapFactory.decodeStream(stream, null, options);
            if (result == null) throw new IOException("Não foi possível abrir a imagem");
            return result;
        }
    }

    public static Bitmap render(Context context, int template, List<Uri> uris,
                                boolean includeText, String title, String leftLabel, String rightLabel) throws IOException {
        Bitmap output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.rgb(8, 16, 24));
        Bitmap[] input = new Bitmap[uris.size()];
        try {
            for (int i = 0; i < uris.size(); i++) input[i] = readBitmap(context, uris.get(i));
            if (template == 1) renderSplit(canvas, input, includeText, title, leftLabel, rightLabel);
            if (template == 2) renderShow(canvas, input, includeText, title);
            if (template == 3) renderCover(canvas, input, includeText, title);
        } finally {
            for (Bitmap bitmap : input) if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        return output;
    }

    private static void renderSplit(Canvas canvas, Bitmap[] input, boolean text, String title,
                                    String leftLabel, String rightLabel) {
        fillCover(canvas, input[0], new RectF(0, 0, 640, HEIGHT));
        fillCover(canvas, input[1], new RectF(640, 0, WIDTH, HEIGHT));
        dim(canvas, new RectF(0, 0, WIDTH, HEIGHT), 0x57000000);
        // Sombras leves para ganhar contraste sem descaracterizar as imagens.
        if (text) {
            outlinedText(canvas, defaultIfEmpty(leftLabel, "1999"), 325, 420, 205, 575, Paint.Align.CENTER);
            outlinedText(canvas, defaultIfEmpty(rightLabel, "2026"), 952, 420, 205, 575, Paint.Align.CENTER);
        }
        drawContain(canvas, input[2], new RectF(315, -16, 1000, 757));
        Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
        rule.setColor(Color.rgb(15, 15, 15));
        rule.setStrokeWidth(6f);
        canvas.drawLine(640, 0, 640, HEIGHT, rule);
        if (text && title != null && !title.trim().isEmpty()) {
            outlinedText(canvas, title.toUpperCase(Locale.ROOT), 640, 687, 59, 1100, Paint.Align.CENTER);
        }
    }

    private static void renderShow(Canvas canvas, Bitmap[] input, boolean text, String title) {
        gradient(canvas, 0xff14181d, 0xff0b2037);
        // Segunda imagem pode ser fundo ou logo/objeto.
        fillCover(canvas, input[1], new RectF(0, 0, WIDTH, HEIGHT));
        dim(canvas, new RectF(0, 0, WIDTH, HEIGHT), 0xA9000000);
        Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        circle.setColor(0xBB022545);
        canvas.drawCircle(300, 418, 210, circle);
        canvas.drawCircle(1010, 415, 195, circle);
        drawContain(canvas, input[1], new RectF(80, 230, 500, 640));
        if (text) drawHeading(canvas, defaultIfEmpty(title, "PC FRACO"), 640, 294, 164, 1200);
        drawContain(canvas, input[0], new RectF(280, -30, 1005, 790));
        canvas.drawRect(new RectF(0, 0, WIDTH, 7), paint(CYAN));
    }

    private static void renderCover(Canvas canvas, Bitmap[] input, boolean text, String title) {
        fillCover(canvas, input[2], new RectF(0, 0, WIDTH, HEIGHT));
        dim(canvas, new RectF(0, 0, WIDTH, HEIGHT), 0x62000000);
        // Capa à direita: sem mudanças no texto/logotipo da capa original.
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0xAA000000);
        shadow.setShadowLayer(24, 2, 12, Color.BLACK);
        canvas.drawRoundRect(new RectF(775, 35, 1216, 685), 12, 12, shadow);
        drawContain(canvas, input[1], new RectF(775, 35, 1216, 685));
        drawContain(canvas, input[0], new RectF(-65, -35, 810, 780));
        if (text) {
            Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
            shade.setShader(new LinearGradient(0, 470, 0, HEIGHT,
                    0x00000000, 0xCB000000, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 460, 810, HEIGHT, shade);
            drawHeading(canvas, defaultIfEmpty(title, "ATÉ ZERAR"), 395, 658, 116, 740);
        }
    }

    private static void gradient(Canvas c, int left, int right) {
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, WIDTH, HEIGHT, left, right, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, WIDTH, HEIGHT, p);
    }

    private static void dim(Canvas canvas, RectF area, int color) {
        canvas.drawRect(area, paint(color));
    }

    private static Paint paint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(color);
        return p;
    }

    private static void fillCover(Canvas canvas, Bitmap bitmap, RectF area) {
        float sw = bitmap.getWidth();
        float sh = bitmap.getHeight();
        float ratio = Math.max(area.width() / sw, area.height() / sh);
        float viewW = area.width() / ratio;
        float viewH = area.height() / ratio;
        int sx = Math.round((sw - viewW) / 2);
        int sy = Math.round((sh - viewH) / 2);
        Rect source = new Rect(sx, sy, Math.min(bitmap.getWidth(), sx + Math.round(viewW)),
                Math.min(bitmap.getHeight(), sy + Math.round(viewH)));
        canvas.drawBitmap(bitmap, source, area, paint(Color.WHITE));
    }

    private static void drawContain(Canvas canvas, Bitmap bitmap, RectF box) {
        float ratio = Math.min(box.width() / bitmap.getWidth(), box.height() / bitmap.getHeight());
        float w = bitmap.getWidth() * ratio;
        float h = bitmap.getHeight() * ratio;
        float x = box.left + (box.width() - w) / 2f;
        float y = box.top + (box.height() - h) / 2f;
        canvas.drawBitmap(bitmap, null, new RectF(x, y, x + w, y + h), paint(Color.WHITE));
    }

    private static String defaultIfEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static void drawHeading(Canvas c, String text, float centerX, float y, float size, float maxWidth) {
        // Para thumbnails, duas linhas curtas costumam ser mais legíveis que uma longa.
        String formatted = text.toUpperCase(Locale.ROOT).trim();
        String[] words = formatted.split("\\s+");
        if (words.length >= 3 && formatted.length() > 14) {
            int pivot = words.length / 2;
            StringBuilder a = new StringBuilder();
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (i < pivot) a.append(words[i]).append(' ');
                else b.append(words[i]).append(' ');
            }
            outlinedText(c, a.toString().trim(), centerX, y - size * 0.7f, size * 0.85f, maxWidth, Paint.Align.CENTER);
            outlinedText(c, b.toString().trim(), centerX, y, size * 0.85f, maxWidth, Paint.Align.CENTER);
        } else {
            outlinedText(c, formatted, centerX, y, size, maxWidth, Paint.Align.CENTER);
        }
    }

    private static void outlinedText(Canvas c, String value, float x, float y, float wantedSize,
                                     float maxWidth, Paint.Align align) {
        if (value == null || value.trim().isEmpty()) return;
        String text = value.trim();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        p.setTextAlign(align);
        p.setTextSize(wantedSize);
        while (p.measureText(text) > maxWidth && p.getTextSize() > 32) p.setTextSize(p.getTextSize() - 4);
        p.setColor(Color.BLACK);
        p.setStrokeWidth(6f);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);
        c.drawText(text, x, y, p);
        p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(9, 2, 5, 0x99000000);
        p.setColor(Color.WHITE);
        c.drawText(text, x, y, p);
    }
}
