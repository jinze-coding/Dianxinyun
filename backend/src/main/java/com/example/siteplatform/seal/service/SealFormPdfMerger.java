package com.example.siteplatform.seal.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Copies complete documents in order. Each source is released before rendering the next. */
public final class SealFormPdfMerger {
    private SealFormPdfMerger() {}

    public static int merge(Path target, List<Long> ids, Function<Long, byte[]> render,
                            BiConsumer<Integer, Integer> progress) throws IOException {
        try (PDDocument merged = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (int i = 0; i < ids.size(); i++) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("导出已中断");
                try (PDDocument source = Loader.loadPDF(render.apply(ids.get(i)))) {
                    merger.appendDocument(merged, source);
                }
                progress.accept(i + 1, merged.getNumberOfPages());
            }
            merged.getDocumentInformation().setTitle("用印申请单合并打印");
            merged.getDocumentInformation().setProducer("智慧营造用印申请");
            merged.save(target.toFile());
            return merged.getNumberOfPages();
        }
    }
}
