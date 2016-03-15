package ipnat.skel;

/* Strahler_Analysis.bsh
 * IJ BAR: https://github.com/tferr/Scripts#scripts
 *
 * BeanShell script that performs Strahler analysis in ImageJ by repeated elimination of
 * terminal branches of topographic 2D/3D skeletons (http://fiji.sc/Strahler)
 * Tiago Ferreira, 2013-2016
 *
 * Requirements:
 * Up-to-date versions of Ignacio Arganda-Carreras Skeletonize[1] and AnalyzeSkeleton[2]
 * plugins. Both are bundled with Fiji (http://fiji.sc/) but can be otherwise downloaded
 * from http://jenkins.imagej.net/job/Stable-Fiji/ws/Fiji.app/plugins/
 * [1] http://fiji.sc/Skeletonize3D
 * [2] http://fiji.sc/AnalyzeSkeleton
 *
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation
 * (http://www.gnu.org/licenses/gpl.txt).
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 */

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import ipnat.Utils;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.SkeletonResult;
import sc.fiji.skeletonize3D.Skeletonize3D_;

public class Strahler implements PlugIn, DialogListener {

	/* Default value for max. number of pruning cycles */
	int maxPruning = 10;

	/* Default option for loop detection */
	int pruneChoice = AnalyzeSkeleton_.SHORTEST_BRANCH;

	/* Default option for 'root-protection' ROI */
	boolean protectRoot = true;

	/* Default option for 'iteration-stack' output */
	boolean outIS = false;

	/* Default option for verbose mode */
	boolean verbose = true;

	/* Default option for tabular option */
	boolean tabular = false;

	/* Remove isolated pixels from thinned images? */
	boolean erodeIsolatedPixels = true;

	/* Title of main results window */
	String STRAHLER_TABLE = "Strahler_Table";

	/* Title of detailed results window */
	String VERBOSE_TABLE = "Strahler_Iteration_Log";

	/* Version of the program */
	String VERSION = "1.5.1 2016.01.19";

	/* Flag for 'root-protective' ROI */
	private static boolean validRootRoi;

	ImageProcessor ip;
	int imgChoice;
	boolean protecRoot;

	@Override
	public void run(final String arg) {

		final Utils utils = new ipnat.Utils();
		if (!utils.validSkelDependencies())
			return;

		// Before starting we'll inform non-Fiji users of required dependencies
		try {
			Class.forName("sc.fiji.analyzeSkeleton.AnalyzeSkeleton_");
			Class.forName("sc.fiji.skeletonize3D.Skeletonize3D_");
		} catch (final ClassNotFoundException e) {
			String msg = "\n**** Strahler Analysis Error: Required file(s) not found:\n" + e + "\n \n"
					+ "Strahler Analysis requires a recent version of AnalyzeSkeleton_.jar and Skeletonize3D_.jar.\n"
					+ "The easiest way to install these files is to use Fiji:\n";
			try {
				Class.forName("net.imagej.ui.swing.updater.ImageJUpdater");
			} catch (final ClassNotFoundException ignored) {
				msg += "Please download a modern version of ImageJ from http://imagej.net/Downloads, then\n";
			}
			msg += "Run Help>Update and ensure you are subscribed to the Fiji update site.\n \n"
					+ "Please visit http://forum.imagej.net/ if you come across any hurdles.";
			// IJ.handleException(e);
			IJ.log(msg);
			IJ.error("Strahler Analysis Error",
					"Dependencies not found!\nCheck the Log window for installation details.");
			return;
		}

		// Retrieve analysis image and its ROI
		final ImagePlus srcImp = WindowManager.getCurrentImage();
		if (!validImage(srcImp))
			return;
		final String title = srcImp.getTitle();
		final Roi roi = srcImp.getRoi();
		validRootRoi = (roi != null && roi.getType() == Roi.RECTANGLE);

		// TODO: 3D Roots are special. We need to:
		// 1) Check if ROI is associated with all slices or just one
		// 2) Ignore counts above/below the ROI, as needed
		// 3) Extend ip operations to stack
		if (validRootRoi && srcImp.getNSlices() > 1) {
			IJ.error("Strahler Analysis warning", "'Root-ROI' works for 2D but not yet for 3D images.");
			validRootRoi = false;
		}

		// Retrieve grayscale image for intensity-based pruning of skel. loops
		final ImagePlus origImp = getOriginalImp(title);
		if (origImp == null)
			return;

		// Work on a skeletonized copy since we'll be modifing the image
		if (roi != null)
			srcImp.killRoi();
		final ImagePlus imp = srcImp.duplicate();
		if (roi != null)
			srcImp.setRoi(roi);
		ip = imp.getProcessor();
		skeletonizeWithoutHermits(imp);

		// Initialize ResultsTable: main and detailed info
		final ResultsTable rt = getTable(STRAHLER_TABLE);
		final ResultsTable logrt = getTable(VERBOSE_TABLE);

		// Analyze root
		ImagePlus rootImp;
		ImageProcessor rootIp = null;
		SkeletonResult rootResult = null;
		ArrayList<Point> rootEndpointsList = null;
		int nRootEndpoints = 0, nRootJunctions = 0;

		if (validRootRoi && verbose) {

			// Duplicate entire canvas. Ignore tree(s) outside ROI
			rootImp = imp.duplicate();
			rootIp = rootImp.getProcessor();
			rootIp.setValue(0.0);
			rootIp.fillOutside(roi);

			// Get root properties
			final AnalyzeSkeleton_ root = new AnalyzeSkeleton_();
			root.setup("", rootImp);
			rootResult = root.run(pruneChoice, false, false, origImp, true, false);
			rootImp.flush();

			// We assume ROI contains only end-point branches, slab voxels and
			// no junction points. We'll thus remove end-points at ROI
			// boundaries
			nRootJunctions = sum(rootResult.getJunctions());
			rootEndpointsList = rootResult.getListOfEndPoints();
			final ListIterator<Point> it = rootEndpointsList.listIterator();
			final Rectangle r = roi.getBounds();
			while (it.hasNext()) {
				final Point p = it.next();
				if (p.x == r.x || p.y == r.y || p.x == r.x + r.getWidth() - 1 || p.y == r.y + r.getHeight() - 1)
					it.remove();
			}
			rootResult.setListOfEndPoints(rootEndpointsList);
			nRootEndpoints = rootEndpointsList.size();

		}

		// Initialize display images. Use Z-projections to populate
		// iteration stack when dealing with 3D skeletons
		final int nSlices = imp.getNSlices();
		ZProjector zp = null;

		final ImageStack iterationStack = new ImageStack(imp.getWidth(), imp.getHeight());
		if (nSlices > 1) {
			zp = new ZProjector(imp);
			zp.setMethod(ZProjector.MAX_METHOD);
			zp.setStartSlice(1);
			zp.setStopSlice(nSlices);
		}

		// Initialize AnalyzeSkeleton_
		final AnalyzeSkeleton_ as = new AnalyzeSkeleton_();
		as.setup("", imp);

		// Perform the iterative pruning
		int order = 1, nEndpoints = 0, nJunctions = 0, nJunctions2 = 0;
		ArrayList<Point> endpointsList = null, junctionsList = null;
		String errorMsg = "";

		do {

			IJ.showStatus("Retrieving measurements for order " + order + "...");
			IJ.showProgress(order, maxPruning);

			// (Re)skeletonize image
			if (order > 1)
				skeletonizeWithoutHermits(imp);

			// Get properties of loop-resolved tree(s)
			final SkeletonResult sr = as.run(pruneChoice, false, false, origImp, true, false);
			nEndpoints = sum(sr.getEndPoints());
			nJunctions = sum(sr.getJunctions());

			if (order == 1) {
				// Remember initial properties
				endpointsList = sr.getListOfEndPoints();
				junctionsList = sr.getListOfJunctionVoxels();

				// Do not include root in 1st order calculations
				nEndpoints -= nRootEndpoints;
				nJunctions -= nRootJunctions;
			}

			// Is it worth proceeding?
			if (nEndpoints == 0 || nJunctions2 == nJunctions) {
				errorMsg = "Error! Iteration " + order + " aborted: ";
				errorMsg += (nEndpoints == 0) ? "No end-poins found" : "Unsolved loop(s) detected";
				break;
			}

			// Add current tree(s) to debug animation
			ImageProcessor ipd;
			if (nSlices > 1) {
				zp.doProjection();
				ipd = zp.getProjection().getProcessor();
			} else {
				ipd = ip.duplicate();
			}
			iterationStack.addSlice("Order " + IJ.pad(order, 2), ipd);

			// Report properties of pruned structures
			if (verbose) {
				logrt.incrementCounter();
				logrt.addLabel("Image", title);
				logrt.addValue("Structure", "Skel. at iteration " + Integer.toString(order));
				logrt.addValue("Notes", errorMsg);
				logrt.addValue("# Trees", sr.getNumOfTrees());
				logrt.addValue("# Branches", sum(sr.getBranches()));
				logrt.addValue("# End-points", nEndpoints);
				logrt.addValue("# Junctions", nJunctions);
				logrt.addValue("# Triple points", sum(sr.getTriples()));
				logrt.addValue("# Quadruple points", sum(sr.getQuadruples()));
				logrt.addValue("Average branch length", average(sr.getAverageBranchLength()));
			}

			// Remember main results
			nJunctions2 = nJunctions;

			// Eliminate end-points
			as.run(pruneChoice, true, false, origImp, true, false, roi);

		} while (order++ <= maxPruning && nJunctions > 0);

		// Set counter to the de facto order
		order -= 1;

		// Append root properties to log table
		if (validRootRoi && verbose) {

			// Check if ROI contains unexpected structures
			final String msg = (nRootJunctions > 0) ? "Warning: ROI contains ramified root(s)"
					: "Root-branches inferred from ROI";
			logrt.incrementCounter();
			logrt.addLabel("Image", title);
			logrt.addValue("Structure", "Root");
			logrt.addValue("Notes", msg);
			logrt.addValue("# Trees", rootResult.getNumOfTrees());
			logrt.addValue("# Branches", sum(rootResult.getBranches()));
			logrt.addValue("# End-points", nRootEndpoints);
			logrt.addValue("# Junctions", nRootJunctions);
			logrt.addValue("# Triple points", sum(rootResult.getTriples()));
			logrt.addValue("# Quadruple points", sum(rootResult.getQuadruples()));
			logrt.addValue("Average branch length", average(rootResult.getAverageBranchLength()));

		}

		// Safety check
		if (iterationStack == null || iterationStack.getSize() < 1) { // TODO:
																		// Make
																		// error
																		// more
																		// informative
			IJ.error("Strahler Analysis Error", "Could not complete analysis!\n" + "Enable verbose mode and check "
					+ VERBOSE_TABLE + " for details.");
			return;
		}

		// Create iteration stack
		final Calibration cal = srcImp.getCalibration();
		final ImagePlus imp2 = new ImagePlus("StrahlerIteration_" + title, iterationStack);
		imp2.setCalibration(cal);
		if (outIS) {
			if (validRootRoi) {
				iterationStack.addSlice("Root", rootIp);
				paintPoints(iterationStack, rootEndpointsList, 255, "Root end-points");
				imp2.setRoi(roi);
			}
			paintPoints(iterationStack, endpointsList, 255, "End-points");
			paintPoints(iterationStack, junctionsList, 255, "Junction-points");
		}

		// Generate Strahler mask
		zp = new ZProjector(imp2);
		zp.setMethod(ZProjector.SUM_METHOD);
		zp.setStartSlice(1);
		zp.setStopSlice(order);
		zp.doProjection();
		final ImageProcessor ip3 = zp.getProjection().getProcessor().convertToShortProcessor(false);
		clearPoints(ip3, junctionsList); // disconnect branches
		ip3.multiply(1 / 255.0); // map intensities to Strahler orders
		final ImagePlus imp3 = new ImagePlus("StrahlerMask_" + title, ip3);
		imp3.setCalibration(cal);

		// Measure segmented orders
		double prevNbranches = Double.NaN;
		for (int i = 1; i <= order; i++) {

			// Segment branches by order
			final ImagePlus maskImp = imp3.duplicate(); // Calibration will be
			// retained
			IJ.setThreshold(maskImp, i, i); // TODO: Use
											// ImagePlus/ImageProcessor API
			IJ.run(maskImp, "Convert to Mask", "");

			// Analyze segmented order
			final AnalyzeSkeleton_ maskAs = new AnalyzeSkeleton_();
			maskAs.setup("", maskImp);
			final SkeletonResult maskSr = maskAs.run(pruneChoice, false, false, origImp, true, false);
			maskImp.flush();

			// Since all branches are disconnected at this stage, the n. of
			// branches is
			// the same as the # the trees unless zero-branches trees exist,
			// i.e., trees
			// with no slab voxels (defined by just an end-point). We will
			// ignore those
			// trees if the user requested it
			final int nBranches = (erodeIsolatedPixels) ? sum(maskSr.getBranches()) : maskSr.getNumOfTrees();

			// Log measurements
			rt.incrementCounter();
			rt.addLabel("Image", title);
			rt.addValue("Strahler Order", i);
			rt.addValue("# Branches", nBranches);
			rt.addValue("Ramification ratios", prevNbranches / nBranches);
			rt.addValue("Average branch length", average(maskSr.getAverageBranchLength()));
			rt.addValue("Unit", cal.getUnit());
			String noteMsg = "";
			if (i == 1) {
				noteMsg = (erodeIsolatedPixels) ? "Ignoring" : "Including";
				noteMsg += " single-point arbors...";
			}
			rt.addValue("Notes", noteMsg);

			// Remember results for previous order
			prevNbranches = nBranches;

		}

		// Append any errors to last row
		rt.addValue("Notes", errorMsg);

		// Display outputs
		if (!tabular) {
			if (outIS)
				imp2.show();
			ip3.setMinAndMax(0, order);
			try {
				Class.forName("bogus");
				// ip3.setColorModel(Sholl_Utils.matlabJetColorMap(0));
			} catch (final ClassNotFoundException ignored) {
				IJ.run(imp3, "Fire", "");
			}
			if (validRootRoi)
				imp3.setRoi(roi);
			imp3.show();
			addCalibrationBar(imp3, Math.min(order, 5));
		}
		if (verbose)
			logrt.show(VERBOSE_TABLE);
		rt.show(STRAHLER_TABLE);

		IJ.showProgress(0, 0);
		IJ.showTime(imp, imp.getStartTime(), "Strahler Analysis concluded... ");
		imp.flush();

	}

	/* Checks if image fulfills analysis requirements */
	boolean validImage(final ImagePlus imp) {
		final boolean valid = imp != null && imp.getBitDepth() == 8;
		if (!valid)
			IJ.error("Strahler Analysis", "An 8-bit image is required but none was found.");
		return valid;
	}

	/*
	 * Creates the dialog prompt, retrieving the image with the original
	 * structure. While it is unlikely that the iterative pruning of terminal
	 * branches will cause new loops on pre-existing skeletons, offering the
	 * option to resolve loops with intensity based methods remains useful
	 * specially when analyzing non-thinned grayscale images.
	 */
	ImagePlus getOriginalImp(final String grayscaleImgChoice) {

		final GenericDialog gd = new GenericDialog("Strahler Analysis v" + VERSION);
		final Font headerFont = new Font("SansSerif", Font.BOLD, 12);
		gd.setSmartRecording(true);

		// Part 1. Main Options
		gd.setInsets(0, 0, 0);
		gd.addMessage("Strahler Numbering:", headerFont);
		gd.addSlider("     Max. n. of iterations:", 1, 20, maxPruning);
		gd.addCheckbox("Infer root end-points from rectangular ROI", protectRoot);
		gd.addCheckbox("Ignore single-point arbors (Isolated pixels)", erodeIsolatedPixels);

		// Part 2: Loop elimination
		gd.setInsets(25, 0, 0);
		gd.addMessage("Elimination of Skeleton Loops:", headerFont);
		gd.addChoice("Method:", AnalyzeSkeleton_.pruneCyclesModes, AnalyzeSkeleton_.pruneCyclesModes[pruneChoice]);

		// 8-bit grayscale is the only image type recognized by
		// AnalyzeSkeleton_,
		// so we'll provide the user with a pre-filtered list of valid choices
		final ArrayList<Integer> validIds = new ArrayList<Integer>();
		final ArrayList<String> validTitles = new ArrayList<String>();
		final int[] ids = WindowManager.getIDList();
		for (int i = 0; i < ids.length; ++i) {
			final ImagePlus imp = WindowManager.getImage(ids[i]);
			if (imp.getBitDepth() == 8) { // TODO: ignore composites?
				validIds.add(ids[i]);
				validTitles.add(imp.getTitle());
			}
		}
		gd.addChoice("8-bit grayscale image:", validTitles.toArray(new String[validTitles.size()]), grayscaleImgChoice);

		// Part 3: Output
		gd.setInsets(25, 0, 0);
		gd.addMessage("Output Options:", headerFont);
		gd.addCheckbox("Display_iteration stack", outIS);
		gd.addCheckbox("Show detailed information", verbose);
		gd.addCheckbox("Tabular data only (no image output)", tabular);

		gd.addHelp("http://fiji.sc/Strahler");
		gd.addDialogListener(this);
		dialogItemChanged(gd, null);
		gd.showDialog();

		return (gd.wasCanceled()) ? null : WindowManager.getImage(validIds.get(imgChoice));
	}

	/* Retrive dialog options using the DialogListener interface */
	@Override
	public boolean dialogItemChanged(final GenericDialog gd, final java.awt.AWTEvent e) {

		maxPruning = (int) gd.getNextNumber();
		protecRoot = gd.getNextBoolean();
		erodeIsolatedPixels = gd.getNextBoolean();
		pruneChoice = gd.getNextChoiceIndex();
		imgChoice = gd.getNextChoiceIndex();
		outIS = gd.getNextBoolean();
		verbose = gd.getNextBoolean();
		tabular = gd.getNextBoolean();

		// Enable/Disable key components of GenericDialog
		final Choice cImgChoice = (Choice) gd.getChoices().elementAt(1);
		cImgChoice.setEnabled((pruneChoice > 1) ? true : false);
		final Vector<?> checkboxes = gd.getCheckboxes();
		final Checkbox roiOption = (Checkbox) checkboxes.elementAt(0);
		roiOption.setEnabled(validRootRoi);
		final Checkbox stackOption = (Checkbox) checkboxes.elementAt(2);
		stackOption.setEnabled(!tabular);
		return !gd.wasCanceled();

	}

	/*
	 * Returns the table of the specified window title setting common properties
	 */
	ResultsTable getTable(final String title) {
		ResultsTable rt = null;
		final Window window = WindowManager.getWindow(title);
		if (window != null)
			rt = ((TextWindow) window).getTextPanel().getResultsTable();
		if (rt == null)
			rt = new ResultsTable();
		rt.setPrecision(5);
		rt.setNaNEmptyCells(true);
		rt.showRowNumbers(false);
		return rt;
	}

	/* Returns the sum of elements of an int[] array */
	int sum(final int[] array) {
		int sum = 0;
		if (array != null)
			for (final int i : array)
				sum += i;
		return sum;
	}

	/* Returns the sum of elements of a double[] array */
	double sum(final double[] array) {
		double sum = 0;
		if (array != null)
			for (final double i : array)
				sum += i;
		return sum;
	}

	/* Returns the average of an int[]/double[] array */
	double average(final double[] array) {
		return sum(array) / array.length;
	}

	/* Returns the average of an int[]/double[] array */
	double average(final int[] array) {
		return sum(array) / array.length;
	}

	/*
	 * Paints point positions. NB: BeanShell does not seem to suport generics:
	 * paintPoints(ImageStack stack, ArrayList<Point> points, int value, String
	 * label) triggers a ParseException
	 */
	void paintPoints(final ImageStack stack, final ArrayList<Point> points, final int value, final String sliceLabel) {
		if (points != null) {
			final ImageProcessor ipp = ip.createProcessor(stack.getWidth(), stack.getHeight());
			for (int j = 0; j < points.size(); j++) {
				final Point point = points.get(j);
				ipp.putPixel(point.x, point.y, value);
			}
			stack.addSlice(sliceLabel, ipp);
		}
	}

	/* Clears point positions */
	void clearPoints(final ImageProcessor ip, final ArrayList<Point> points) {
		if (points != null) {
			for (int j = 0; j < points.size(); j++) {
				final Point point = points.get(j);
				ip.putPixel(point.x, point.y, 0);
			}
		}
	}

	/*
	 * Skeletonization method that erodes the thinned structure in order to
	 * eliminate isolated pixels. Thinning and pruning may give rise to single
	 * point arbors. These 'debris' trees have 1 end-point but no branches or
	 * junctions. If present they overestimate the total number of end-points
	 */
	void skeletonizeWithoutHermits(final ImagePlus imp) {
		final Skeletonize3D_ thin = new Skeletonize3D_();
		thin.setup("", imp);
		thin.run(null);
		if (erodeIsolatedPixels) {
			final ImageStack stack = imp.getStack();
			for (int i = 1; i <= stack.getSize(); i++)
				((ByteProcessor) stack.getProcessor(i)).erode(8, 0);
		}
	}

	/*
	 * Runs ij.plugin.CalibrationBar on the specified image using a suitable
	 * scale
	 */
	void addCalibrationBar(final ImagePlus imp, final int nLabels) {
		final ImageCanvas ic = imp.getCanvas();
		double zoom = 1.0;
		final double mag = (ic != null) ? ic.getMagnification() : 1.0;
		if (zoom <= 1 && mag < 1)
			zoom = 1.0 / mag;
		IJ.run(imp, "Calibration Bar...", "fill=Black label=White number=" + nLabels + " zoom=" + zoom + " overlay");
	}
}
