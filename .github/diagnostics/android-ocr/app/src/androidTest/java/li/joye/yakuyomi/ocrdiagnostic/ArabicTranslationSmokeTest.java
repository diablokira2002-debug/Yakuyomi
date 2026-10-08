package li.joye.yakuyomi.ocrdiagnostic;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ArabicTranslationSmokeTest {

    // Actual English dialogue transcribed from the user's five sample page screenshots.
    private static final String[] SOURCES = {
        "SHE'LL DO FINE.",
        "SHE'S A SMART WOMAN.",
        "I TOLD YOU.",
        "IT'S ALWAYS BALANCED.",
        "THIS TIME.",
        "WHAT?"
    };
    private static final String TAG = "YakuyomiArabicDiag";

    @Test
    public void translateUserMangaDialoguesToArabic() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Translator translator = Translation.getClient(new TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.ARABIC)
            .build());

        StringBuilder report = new StringBuilder();
        report.append("Yakuyomi Arabic ML Kit Translation Smoke\n");
        report.append("Android=").append(android.os.Build.VERSION.SDK_INT).append('\n');
        report.append("Device=").append(android.os.Build.MODEL).append('\n');
        boolean passed = false;
        try {
            report.append("Download or verify English -> Arabic model...\n");
            Tasks.await(translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()),
                180, TimeUnit.SECONDS);
            report.append("MODEL_READY=true\n");

            for (String source : SOURCES) {
                String output = Tasks.await(translator.translate(source), 90, TimeUnit.SECONDS);
                boolean isArabic = output != null && output.codePoints().anyMatch(cp ->
                    (cp >= 0x0621 && cp <= 0x064A) ||
                    (cp >= 0x066E && cp <= 0x06D3) ||
                    (cp >= 0x0750 && cp <= 0x077F));
                report.append("EN: ").append(source).append("\nAR: ").append(output)
                    .append("\nCONTAINS_ARABIC=").append(isArabic).append("\n\n");
                assertTrue("Translation must contain Arabic letters for: " + source +
                    ", actual: " + output, isArabic);
            }
            passed = true;
        } catch (Throwable failure) {
            report.append("ERROR=").append(failure.getClass().getSimpleName())
                .append(": ").append(failure.getMessage()).append('\n');
            throw failure;
        } finally {
            report.append("ALL_PASSED=").append(passed).append('\n');
            Log.i(TAG, report.toString());
            try {
                File dir = new File(context.getExternalFilesDir(null), "diagnostics");
                if (!dir.exists()) dir.mkdirs();
                try (FileOutputStream out = new FileOutputStream(new File(dir, "arabic-smoke.txt"))) {
                    out.write(report.toString().getBytes(StandardCharsets.UTF_8));
                }
            } finally {
                translator.close();
            }
        }
    }
}
