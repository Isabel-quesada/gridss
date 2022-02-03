package gridss.cmdline;

import au.edu.wehi.idsv.*;
import au.edu.wehi.idsv.util.AutoClosingIterator;
import au.edu.wehi.idsv.util.DeterministicIterators;
import au.edu.wehi.idsv.util.FileHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.ProgressLogger;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.broadinstitute.barclay.argparser.Argument;
import picard.cmdline.StandardOptionDefinitions;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Base class used to transform a VCF breakpoint call set given the full evidence available.
 * 
 * 
 * @author Daniel Cameron
 *
 */
public abstract class VcfTransformCommandLineProgram extends FullEvidenceCommandLineProgram {
	private static final Log log = Log.getInstance(VcfTransformCommandLineProgram.class);
	@Argument(shortName="VCF", doc="Coordinate sorted VCF file")
    public File INPUT_VCF;
	@Argument(shortName=StandardOptionDefinitions.OUTPUT_SHORT_NAME, doc="VCF structural variation calls.")
    public File OUTPUT_VCF;
	public abstract CloseableIterator<VariantContextDirectedEvidence> iterator(CloseableIterator<VariantContextDirectedEvidence> calls, ExecutorService threadpool);
	private VCFHeader inputHeader = null;
	@Override
	public int doWork(ExecutorService threadpool) throws IOException, InterruptedException, ExecutionException {
		if (INPUT != null) {
			for (File f : INPUT) {
				IOUtil.assertFileIsReadable(f);
			}
		}
		if (ASSEMBLY != null) {
			for (File a : ASSEMBLY) {
				IOUtil.assertFileIsReadable(a);
			}
		}
		IOUtil.assertFileIsReadable(INPUT_VCF);
		IOUtil.assertFileIsWritable(OUTPUT_VCF);
		if (INPUT_VCF.equals(OUTPUT_VCF)) {
			log.error("Input and output files must be different.");
			return 1;
		}
		log.info("Annotating variants in " + INPUT_VCF);
		try (CloseableIterator<VariantContextDirectedEvidence> it = iterator(getBreakends(INPUT_VCF), threadpool)) {
			saveVcf(OUTPUT_VCF, getAllCalls(INPUT_VCF, it));
		}
		log.info("Annotated variants written to " + OUTPUT_VCF);
		return 0;
	}
	public CloseableIterator<VariantContextDirectedEvidence> getBreakends(File file) {
		VCFFileReader vcfReader = new VCFFileReader(file, false);
		CloseableIterator<VariantContext> it = vcfReader.iterator();
		Iterator<IdsvVariantContext> idsvIt = Iterators.transform(it, variant -> IdsvVariantContext.create(getContext().getDictionary(), null, variant));
		Iterator<VariantContextDirectedEvidence> beit = Iterators.filter(idsvIt, VariantContextDirectedEvidence.class);
		// resort by evidence start
		beit = new DirectEvidenceWindowedSortingIterator<>(getContext(), SAMEvidenceSource.maximumWindowSize(getContext(), getSamEvidenceSources(), getAssemblySource()), beit);
		return new AutoClosingIterator<VariantContextDirectedEvidence>(beit, it, vcfReader);
	}
	public Iterator<IdsvVariantContext> getAllCalls(File file, CloseableIterator<VariantContextDirectedEvidence> breakendCalls) {
		VCFFileReader vcfReader = new VCFFileReader(file, false);
		if (inputHeader != null) {
			inputHeader = vcfReader.getFileHeader();
		}
		CloseableIterator<VariantContext> it = vcfReader.iterator();
		Iterator<IdsvVariantContext> idsvIt = Iterators.transform(it, variant -> IdsvVariantContext.create(getContext().getDictionary(), null, variant));
		Iterator<IdsvVariantContext> nonbeIt = Iterators.filter(idsvIt, variant -> !(variant instanceof VariantContextDirectedEvidence));
		// sort back to nominal VCF position
		Iterator<VariantContextDirectedEvidence> bpit = new VariantContextWindowedSortingIterator<>(getContext(), SAMEvidenceSource.maximumWindowSize(getContext(), getSamEvidenceSources(), getAssemblySource()), breakendCalls);
		Iterator<IdsvVariantContext> mergedIt = DeterministicIterators.mergeSorted(ImmutableList.of(bpit, nonbeIt), IdsvVariantContext.ByLocationStart);
		return new AutoClosingIterator<>(mergedIt, vcfReader, it);
	}
	protected void saveVcf(File file, Iterator<IdsvVariantContext> calls) throws IOException {
		File tmp = gridss.Defaults.OUTPUT_TO_TEMP_FILE ? FileSystemContext.getWorkingFileFor(file) : file;
		final ProgressLogger writeProgress = new ProgressLogger(log);
		try (VariantContextWriter vcfWriter = getContext().getVariantContextWriter(tmp, getOutputHeader(), true)) {
			while (calls.hasNext()) {
				IdsvVariantContext record = calls.next();
				vcfWriter.add(record);
				writeProgress.record(record.getContig(), record.getStart());
			}
		}
		if (tmp != file) {
			FileHelper.move(tmp, file, true);
		}
	}
	protected VCFHeader getInputHeader() {
		if (inputHeader == null) {
			try (VCFFileReader vcfReader = new VCFFileReader(INPUT_VCF, false)) {
				inputHeader = vcfReader.getFileHeader();
			}
		}
		return inputHeader;
	}
	protected VCFHeader getOutputHeader() {
		VCFHeader header = getInputHeader();
		assert(header != null);
		return header;
	}
}
