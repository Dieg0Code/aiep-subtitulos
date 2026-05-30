package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;

/**
 * Drives a {@link PrintDocumentAdapter} (e.g. WebView's) to write a PDF straight
 * to a file, with no system print dialog.
 *
 * <p>Lives in the {@code android.print} package on purpose: the constructors of
 * {@link PrintDocumentAdapter.LayoutResultCallback} and
 * {@link PrintDocumentAdapter.WriteResultCallback} are package-private, so they
 * can only be subclassed from within this package. This is the standard
 * workaround for the "constructor is package-private" Kotlin/Java error.
 */
public final class WebViewPdfPrinter {
    private WebViewPdfPrinter() {}

    public interface Callback {
        void onSuccess(File file);
        void onError(Throwable error);
    }

    public static void print(
            final PrintDocumentAdapter adapter,
            final PrintAttributes attributes,
            final File outFile,
            final Callback callback) {
        adapter.onLayout(
                attributes,
                attributes,
                new CancellationSignal(),
                new PrintDocumentAdapter.LayoutResultCallback() {
                    @Override
                    public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                        final ParcelFileDescriptor pfd;
                        try {
                            pfd = ParcelFileDescriptor.open(
                                    outFile,
                                    ParcelFileDescriptor.MODE_READ_WRITE
                                            | ParcelFileDescriptor.MODE_CREATE
                                            | ParcelFileDescriptor.MODE_TRUNCATE);
                        } catch (Exception e) {
                            callback.onError(e);
                            return;
                        }
                        adapter.onWrite(
                                new PageRange[]{PageRange.ALL_PAGES},
                                pfd,
                                new CancellationSignal(),
                                new PrintDocumentAdapter.WriteResultCallback() {
                                    @Override
                                    public void onWriteFinished(PageRange[] pages) {
                                        closeQuietly(pfd);
                                        callback.onSuccess(outFile);
                                    }

                                    @Override
                                    public void onWriteFailed(CharSequence error) {
                                        closeQuietly(pfd);
                                        callback.onError(new IOException(
                                                error != null ? error.toString() : "No se pudo escribir el PDF"));
                                    }

                                    @Override
                                    public void onWriteCancelled() {
                                        closeQuietly(pfd);
                                        callback.onError(new IOException("Generación de PDF cancelada"));
                                    }
                                });
                    }

                    @Override
                    public void onLayoutFailed(CharSequence error) {
                        callback.onError(new IOException(
                                error != null ? error.toString() : "No se pudo preparar el PDF"));
                    }

                    @Override
                    public void onLayoutCancelled() {
                        callback.onError(new IOException("Preparación de PDF cancelada"));
                    }
                },
                new Bundle());
    }

    private static void closeQuietly(ParcelFileDescriptor pfd) {
        try {
            pfd.close();
        } catch (IOException ignored) {
        }
    }
}
