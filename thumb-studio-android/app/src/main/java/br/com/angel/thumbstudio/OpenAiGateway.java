package br.com.angel.thumbstudio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Cliente do SEU servidor: a chave OpenAI fica no servidor, nunca no APK. */
public final class OpenAiGateway {
    private OpenAiGateway() { }

    public static Bitmap generate(Context context, String baseUrl, String appToken, int template,
                                  List<Uri> sources, Uri customReference,
                                  String title, String description, boolean includeText,
                                  String labelLeft, String labelRight) throws IOException {
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (!normalized.startsWith("https://"))
            throw new IOException("Use um endereço HTTPS do servidor. HTTP não é permitido.");
        if (appToken.trim().isEmpty()) throw new IOException("Informe seu token de acesso ao servidor.");
        String boundary = "ThumbStudio-" + UUID.randomUUID();
        HttpURLConnection connection = (HttpURLConnection) new URL(normalized + "/generate").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(240000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("X-App-Token", appToken);
        connection.setChunkedStreamingMode(64 * 1024);
        try {
            try (OutputStream stream = connection.getOutputStream()) {
                field(stream, boundary, "template_id", String.valueOf(template));
                field(stream, boundary, "title", title);
                field(stream, boundary, "instruction", description);
                field(stream, boundary, "include_text", String.valueOf(includeText));
                field(stream, boundary, "left_label", labelLeft);
                field(stream, boundary, "right_label", labelRight);
                if (template == 4) {
                    if (customReference == null) throw new IOException("Selecione o modelo de referência.");
                    attachment(stream, boundary, "model_reference", "modelo.png", context.getContentResolver().openInputStream(customReference));
                } else {
                    attachment(stream, boundary, "model_reference", "modelo.png",
                            context.getAssets().open("modelo_" + template + ".png"));
                }
                for (int i = 0; i < sources.size(); i++) {
                    Uri uri = sources.get(i);
                    if (uri != null)
                        attachment(stream, boundary, "images", "fonte_" + (i + 1) + ".png",
                                context.getContentResolver().openInputStream(uri));
                }
                stream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                try (InputStream err = connection.getErrorStream()) {
                    String detail = err != null ? new String(readLimited(err, 8192), StandardCharsets.UTF_8) : "";
                    throw new IOException("Servidor retornou " + status + ": " + detail);
                }
            }
            try (InputStream stream = connection.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                if (bitmap == null) throw new IOException("O servidor não retornou uma imagem válida.");
                return bitmap;
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void field(OutputStream stream, String boundary, String key, String value) throws IOException {
        String item = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + key + "\"\r\n\r\n" +
                (value == null ? "" : value) + "\r\n";
        stream.write(item.getBytes(StandardCharsets.UTF_8));
    }

    private static void attachment(OutputStream stream, String boundary, String name,
                                   String filename, InputStream content) throws IOException {
        if (content == null) throw new IOException("Não foi possível abrir " + filename);
        String start = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name +
                "\"; filename=\"" + filename + "\"\r\nContent-Type: image/png\r\n\r\n";
        stream.write(start.getBytes(StandardCharsets.UTF_8));
        try (InputStream source = content) {
            byte[] buffer = new byte[65536];
            int count;
            int total = 0;
            while ((count = source.read(buffer)) >= 0) {
                total += count;
                if (total > 15 * 1024 * 1024) throw new IOException("Uma imagem excede 15 MB.");
                stream.write(buffer, 0, count);
            }
        }
        stream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while (out.size() < max && (read = in.read(buffer, 0, Math.min(buffer.length, max - out.size()))) != -1)
            out.write(buffer, 0, read);
        return out.toByteArray();
    }
}
