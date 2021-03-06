package io.sloeber.core;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import io.sloeber.core.api.BoardDescriptor;
import io.sloeber.core.api.CodeDescriptor;
import io.sloeber.core.api.CompileOptions;
import io.sloeber.core.api.ConfigurationDescriptor;
import io.sloeber.core.api.LibraryManager;
import io.sloeber.core.api.PackageManager;
import io.sloeber.providers.MCUBoard;
import io.sloeber.providers.Teensy;

@SuppressWarnings({"nls","unused"})
@RunWith(Parameterized.class)
public class CreateAndCompileArduinoIDEExamplesOnTeensyTest {
	private static int mCounter = 0;
	private CodeDescriptor myCodeDescriptor;
	private String myName;
	private Examples myExample;
	private static int totalFails = 0;
	private static int maxFails = 500;

	public CreateAndCompileArduinoIDEExamplesOnTeensyTest(String name, CodeDescriptor codeDescriptor, Examples example) {

		myCodeDescriptor = codeDescriptor;
		myName = name;
		myExample = example;
	}

	@SuppressWarnings("rawtypes")
	@Parameters(name = "{index}: {0}")
	public static Collection examples() {
		installAdditionalBoards();

		Shared.waitForAllJobsToFinish();
		LinkedList<Object[]> examples = new LinkedList<>();

		TreeMap<String, IPath> exampleFolders = LibraryManager.getAllArduinoIDEExamples();
		for (Map.Entry<String, IPath> curexample : exampleFolders.entrySet()) {
			String fqn = curexample.getKey().trim();
			IPath examplePath = curexample.getValue();
			Examples example = new Examples(fqn, null, examplePath);
			if (!skipExample(example)) {
				ArrayList<IPath> paths = new ArrayList<>();
				paths.add(examplePath);
				CodeDescriptor codeDescriptor = CodeDescriptor.createExample(false, paths);

				Object[] theData = new Object[] { "Example:" + fqn, codeDescriptor, example };
				examples.add(theData);
			}
		}

		return examples;

	}

	private static boolean skipExample(Examples example) {
		// no need to skip examples in this test
		return false;
	}

	public static void installAdditionalBoards() {
		if (MySystem.getTeensyPlatform().isEmpty()) {
			System.err.println("ERROR: Teensy not installed/configured skipping tests!!!");
		} else {
			PackageManager.addPrivateHardwarePath(MySystem.getTeensyPlatform());
		}

	}

	public void testExample(MCUBoard board) {
		// Stop after X fails because
		// the fails stays open in eclipse and it becomes really slow
		// There are only a number of issues you can handle
		// best is to focus on the first ones and then rerun starting with the
		// failures
		if (totalFails >= maxFails) {
			fail("To many fails. Stopping test");
		}

		if (!board.isExampleSupported(myExample)) {
			return;
		}
		Map<String,String> boardOptions=board.getBoardOptions(myExample);
		BoardDescriptor boardDescriptor=board.getBoardDescriptor();
		boardDescriptor.setOptions(boardOptions);
		BuildAndVerify(boardDescriptor);

	}

	@Test
	public void testArduinoIDEExamplesOnTeensy3_6() {
		if (!MySystem.getTeensyPlatform().isEmpty())
			testExample(Teensy.Teensy3_6());
	}

	@Test
	public void testArduinoIDEExamplesOnTeensy3_5() {
		if (!MySystem.getTeensyPlatform().isEmpty())
			testExample(Teensy.Teensy3_5());
	}

	@Test
	public void testArduinoIDEExamplesOnTeensy3_1() {
		if (!MySystem.getTeensyPlatform().isEmpty())
			testExample(Teensy.Teensy3_1());
	}

	@Test
	public void testArduinoIDEExamplesOnTeensy3_0() {
		if (!MySystem.getTeensyPlatform().isEmpty())
			testExample(Teensy.Teensy3_0());
	}

	@Test
	public void testArduinoIDEExamplesOnTeensyLC() {
		if (!MySystem.getTeensyPlatform().isEmpty())
			testExample(Teensy.Teensy_LC());
	}

	@Test
	public void testArduinoIDEExamplesOnTeensyPP2() {
		if (!MySystem.getTeensyPlatform().isEmpty())
			testExample(Teensy.teensypp2());
	}

	@Test
	public void testArduinoIDEExamplesOnTeensy2() {
		if (!MySystem.getTeensyPlatform().isEmpty())
			testExample(Teensy.teensy2());

	}

	public void BuildAndVerify(BoardDescriptor boardDescriptor) {

		IProject theTestProject = null;

		NullProgressMonitor monitor = new NullProgressMonitor();
		String projectName = String.format("%05d_:%s_%s", new Integer(mCounter++), this.myName,
				boardDescriptor.getBoardID());
		try {

			theTestProject = boardDescriptor.createProject(projectName, null,
					ConfigurationDescriptor.getDefaultDescriptors(), this.myCodeDescriptor,  new CompileOptions(null),
					monitor);
			Shared.waitForAllJobsToFinish(); // for the indexer
		} catch (Exception e) {
			e.printStackTrace();
			totalFails++;
			fail("Failed to create the project:" + projectName);
			return;
		}
		try {
			theTestProject.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
			if (Shared.hasBuildErrors(theTestProject)) {
				// try again because the libraries may not yet been added
				Shared.waitForAllJobsToFinish(); // for the indexer
				try {
					Thread.sleep(3000);// seen sometimes the libs were still not
										// added
				} catch (InterruptedException e) {
					// ignore
				}
				theTestProject.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
				if (Shared.hasBuildErrors(theTestProject)) {
					// give up
					totalFails++;
					fail("Failed to compile the project:" + projectName + " build errors");
				} else {
					theTestProject.delete(true, null);
				}
			} else {
				theTestProject.delete(true, null);
			}
		} catch (CoreException e) {
			e.printStackTrace();
			totalFails++;
			fail("Failed to compile the project:" + projectName + " exception");
		}
	}

}
