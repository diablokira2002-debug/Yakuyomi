package li.joye.yakuyomi.ocrdiagnostic;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class OcrChapter35Test {
    private static final String TAG = "YakuyomiOcrDiag";
    private static final int TILE_HEIGHT = 760;
    private static final int TILE_OVERLAP = 180;
    private static final float UPSCALE = 2.0f;

    private static final Map<String, List<String>> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("chapter35_01.jpg", Arrays.asList(
            "SOMETHING'S REALLY STRANGE ABOUT THIS PLACE.",
            "THE COLORS ARE ALL FADED, JUST LIKE OLD PAINT..."
        ));
        EXPECTED.put("chapter35_02.jpg", Arrays.asList(
            "I CAN FEEL AN OMINOUS PRESENCE FROM INSIDE...",
            "BUT..."
        ));
        EXPECTED.put("chapter35_03.jpg", Arrays.asList(
            "WEREN'T YOU TREMBLING BECAUSE YOU WERE SCARED?",
            "I MEAN IT, I'LL BE RIGHT BACK."
        ));
        EXPECTED.put("chapter35_04.jpg", Arrays.asList(
            "THERE'S NOWHERE TO RUN HERE.",
            "I'LL PUT YOU DOWN, STAY HERE.",
            "I'LL BE RIGHT BACK... UGH."
        ));
        EXPECTED.put("chapter35_05.jpg", Arrays.asList(
            "LIKE THE TEA ROOM BEING COMPLETELY EMPTY,",
            "OR SEEING FURNITURE AND DECORATIONS I'VE NEVER NOTICED BEFORE."
        ));
    }

    @Test
    public void chapter35LatinOcrAndTranslationDiagnostic() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Translator translator = Translation.getClient(
            new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.ARABIC)
                .build()
        );

        StringBuilder report = new StringBuilder();
        report.append("Yakuyomi Chapter 35 OCR diagnostic\n");
        report.append("Device ABI=").append(android.os.Build.SUPPORTED_ABIS[0]).append("\n");
        report.append("Android=").append(android.os.Build.VERSION.SDK_INT).append("\n\n");

        boolean translationReady = false;
        try {
            Tasks.await(
                translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()),
                120, TimeUnit.SECONDS
            );
            translationReady = true;
            report.append("ML Kit translation model: READY\n\n");
        } catch (Throwable t) {
            report.append("ML Kit translation model: FAILED: ")
                .append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append("\n\n");
        }

        boolean allExpectedFound = true;

        try {
            for (Map.Entry<String, List<String>> entry : EXPECTED.entrySet()) {
                String asset = entry.getKey();
                Bitmap page = BitmapFactory.decodeStream(context.getAssets().open(asset));
                if (page == null) throw new IllegalStateException("Could not decode " + asset);

                String full = recognize(recognizer, page);
                String tiled = recognizeTiled(recognizer, page);

                report.append("===== ").append(asset).append(" =====\n");
                report.append("size=").append(page.getWidth()).append("x").append(page.getHeight()).append("\n");
                report.append("-- FULL PAGE OCR --\n").append(full).append("\n");
                report.append("-- TILED UPSCALED OCR --\n").append(tiled).append("\n");

                String normalizedFull = normalize(full);
                String normalizedTiled = normalize(tiled);

                for (String expected : entry.getValue()) {
                    boolean fullFound = containsPhrase(normalizedFull, normalize(expected));
                    boolean tiledFound = containsPhrase(normalizedTiled, normalize(expected));
                    report.append("EXPECTED: ").append(expected).append("\n");
                    report.append("  full=").append(fullFound)
                        .append(" tiled=").append(tiledFound).append("\n");

                    if (translationReady && tiledFound) {
                        try {
                            String ar = Tasks.await(translator.translate(expected), 60, TimeUnit.SECONDS);
                            report.append("  ar=").append(ar).append("\n");
                        } catch (Throwable t) {
                            report.append("  translate_error=")
                                .append(t.getClass().getSimpleName()).append(": ")
                                .append(t.getMessage()).append("\n");
                        }
                    }

                    if (!tiledFound) allExpectedFound = false;
                }

                report.append("\n");
                page.recycle();
            }
        } finally {
            recognizer.close();
            translator.close();
            writeReport(context, report.toString());
            Log.i(TAG, "\n" + report);
        }

        assertTrue(
            "At least one supplied Chapter 35 phrase was not recovered by tiled/upscaled ML Kit OCR. " +
            "See yakuyomi-ocr-diagnostic.txt artifact.",
            allExpectedFound
        );
    }

    private static String recognize(TextRecognizer recognizer, Bitmap bitmap) throws Exception {
        Text text = Tasks.await(
            recognizer.process(InputImage.fromBitmap(bitmap, 0)),
            60, TimeUnit.SECONDS
        );
        return text.getText();
    }

    private static String recognizeTiled(TextRecognizer recognizer, Bitmap page) throws Exception {
        StringBuilder out = new StringBuilder();
        int step = TILE_HEIGHT - TILE_OVERLAP;

        for (int top = 0; top < page.getHeight(); top += step) {
            int bottom = Math.min(page.getHeight(), top + TILE_HEIGHT);
            int h = bottom - top;
            if (h <= 0) break;

            Bitmap tile = Bitmap.createBitmap(page, 0, top, page.getWidth(), h);
            Bitmap scaled = Bitmap.createScaledBitmap(
                tile,
                Math.max(1, Math.round(tile.getWidth() * UPSCALE)),
                Math.max(1, Math.round(tile.getHeight() * UPSCALE)),
                true
            );

            String text = recognize(recognizer, scaled);
            if (!text.trim().isEmpty()) {
                out.append("[tile ").append(top).append("-").append(bottom).append("]\n");
                out.append(text).append("\n");
            }

            if (scaled != tile) scaled.recycle();
            tile.recycle();
            if (bottom == page.getHeight()) break;
        }
        return out.toString();
    }

    private static String normalize(String value) {
        return value
            .toUpperCase(Locale.ROOT)
            .replace('’', '\'')
            .replaceAll("[^A-Z0-9]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static boolean containsPhrase(String haystack, String needle) {
        if (haystack.contains(needle)) return true;

        // Allow small OCR punctuation/spacing errors, but not missing words.
        String[] words = needle.split(" ");
        if (words.length <= 2) {
            for (String w : words) {
                if (!haystack.contains(w)) return false;
            }
            return true;
        }

        int found = 0;
        int cursor = 0;
        for (String word : words) {
            int p = haystack.indexOf(word, cursor);
            if (p >= 0) {
                found++;
                cursor = p + word.length();
            }
        }
        return found >= Math.ceil(words.length * 0.90);
    }

    private static void writeReport(Context context, String report) throws Exception {
        File dir = new File(context.getExternalFilesDir(null), "diagnostics");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create " + dir);
        }
        File file = new File(dir, "yakuyomi-ocr-diagnostic.txt");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(report.getBytes(StandardCharsets.UTF_8));
        }
        Log.i(TAG, "REPORT_PATH=" + file.getAbsolutePath());
    }
}
