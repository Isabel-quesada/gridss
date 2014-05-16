package au.edu.wehi.socrates.sam;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import au.edu.wehi.socrates.TestHelper;


public class AnomolousReadAssemblyTest extends TestHelper {
	@Test
	public void assemblyBreakpointQuality_should_be_average_breakpoint_mapq() {
		AnomolousReadAssembly ara = new AnomolousReadAssembly("asd", B("AAAAAAAAAA"), new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }, 5, FWD, 1, 1);
		assertEquals((5+6+7+8+9)/5.0, ara.getAssemblyBreakpointQuality(), 0.00001);
	}
	@Test
	public void getBreakpointBases_should_assume_assembly_on_positive_strand() {
		assertEquals("T", new AnomolousReadAssembly("asd", B("AT"), new byte[] { 0, 1, }, 1, FWD, 1, 1).getBreakpointBases());
		assertEquals("A", new AnomolousReadAssembly("asd", B("AT"), new byte[] { 0, 1, }, 1, BWD, 1, 1).getBreakpointBases());
	}
}
