/*
 * #%L
 * hIPNAT plugins for Fiji distribution of ImageJ
 * %%
 * Copyright (C) 2017 Tiago Ferreira
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package ipnat;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.util.Vector;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.gui.NewImage;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.YesNoCancelDialog;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.util.Tools;
import ij3d.Image3DUniverse;
import ij3d.ImageWindow3D;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sholl.gui.EnhancedGenericDialog;
import stacks.ThreePanes;
import tracing.Path;
import tracing.PathAndFillManager;
import tracing.PointSelectionBehavior;
import tracing.SimpleNeuriteTracer;

// TODO: implement other rending options: ClearVolume
/**
 * Allows SWC/TRACES files to be imported in ImageJ without a priori knowledge
 * of the original image from which tracings were obtained. In cases in which
 * the image is available, it is preferable to script
 * {@link PathAndFillManager} directly.
 */
public class ImportTracings extends SimpleNeuriteTracer implements PlugIn, DialogListener {

	private double xOffset, yOffset, zOffset;
	private double xOffsetGuessed, yOffsetGuessed, zOffsetGuessed;
	private double xScale, yScale, zScale;
	private boolean assumeCoordinatesIndexVoxels;
	private double voxelWidth, voxelHeight, voxelDepth;
	private String voxelUnit;
	private boolean tracesFile;
	private boolean reuseViewer;
	private boolean debug = true;

	/** Default options for SWC import */
	private final double DEFAULT_OFFSET = 0d;
	private final double DEFAULT_SCALE = 1d;
	private final double DEF_VOXEL_SIZE = 1d;
	private final String DEF_VOXEL_UNIT = "unknown";

	public static final int GRAY_3DVIEWER = 0;
	public static final int COLOR_3DVIEWER = 1;
	public static final int COLORMAP_3DVIEWER = 2;
	public static final int UNTAGGED_SKEL = 3;
	public static final int TAGGED_SKEL = 4;
	public static final int ROI_PATHS = 5;

	/** These labels must match the indices of the options above **/
	private final String[] RENDING_OPTIONS = new String[] { "3D paths (monochrome)", "3D paths (STN/SWC-type colors)",
			"3D paths (colored by path ID)", "Untagged skeleton", "Tagged skeleton",
			"2D ROIs (stored in ROI Manager)" };

	private EnhancedGenericDialog settingsDialog;
	private final String PREFS_KEY = "tracing.SWCImportOptionsDialog.";
	private int rendingChoice = COLOR_3DVIEWER;
	private boolean applyOffset, applyScale;
	private File chosenFile;

	/**
	 * Instantiates a new class using sensible defaults for most SWC files.
	 */
	public ImportTracings() {
		applyOffset(DEFAULT_OFFSET, DEFAULT_OFFSET, DEFAULT_OFFSET);
		applyScalingFactor(DEFAULT_SCALE, DEFAULT_SCALE, DEFAULT_SCALE);
		applyCalibration(DEF_VOXEL_SIZE, DEF_VOXEL_SIZE, DEF_VOXEL_SIZE, "");
		assumeCoordinatesIndexVoxels(false);
	}
	public static void main(final String... args) {
		new ImageJ();
		IJ.runPlugIn(ImportTracings.class.getName(), null);
	}

	@Override
	public void run(final String arg) {

		if (chosenFile == null) {
			final OpenDialog od = new OpenDialog("Open .(e)swc or .traces file...", null, null);
			final String directory = od.getDirectory();
			final String fileName = od.getFileName();
			if (fileName == null) // User pressed "Cancel"
				return;
			chosenFile = new File(directory, fileName);
		}

		if (isSWCfile(chosenFile.getName())) {

			if (!getSWCsettingsFromUser())
				return;
			saveSWCSettings();
			autoLoadSWC(chosenFile.getAbsolutePath(), true);

		} else if (isTracesfile(chosenFile.getName())) {

			if (!getTracesRendingChoice())
				return;
			loadTRACES(chosenFile.getAbsolutePath());

		}

		if (pathAndFillManager == null || pathAndFillManager.size() == 0) {
			Utils.error("Invalid File?", "Unable to load tracings from \n" + chosenFile.getAbsolutePath(), null);
			return;
		}

		try {

			renderPaths(rendingChoice);

		} catch (final Exception e) {

			if (!IJ.macroRunning() && chosenFile != null && isSWCfile(chosenFile.getName())
					&& new YesNoCancelDialog(IJ.getInstance(), "Unable to render " + chosenFile.getName(),
							"Re-try with guessed (presumably more suitable) settings?").yesPressed()) {
				pathAndFillManager = null;
				applyCalibration(voxelWidth, voxelHeight, voxelDepth, voxelUnit);
				applyScalingFactor(xScale, yScale, zScale);
				applyOffset(xOffsetGuessed, yOffsetGuessed, zOffsetGuessed);
				try {
					loadSWC(chosenFile.getAbsolutePath(), true);
					renderPaths(rendingChoice);
				} catch (final Exception reloadExc) {
					IPNAT.handleException(reloadExc);
					IPNAT.error("Reload of " + chosenFile.getName() + " failed.\n"
							+ "It is likely that the calculated offsets (required for skeletonize rendering)\n"
							+ "were not appropriate. Assuming the file contains valid data, you could\n"
							+ "try a different rendering choice or change input settings. Running the\n"
							+ "plugin in debug mode may also help.");
				}
			} else {
				IPNAT.handleException(e);
			}

		}

	}

	/**
	 * Render imported paths.
	 *
	 * @param rendingChoice
	 *            either {@link UNTAGGED_SKEL}, {@link TAGGED_SKEL},
	 *            {@link ROI_PATHS}, {@link COLOR_3DVIEWER}, etc.
	 */
	public void renderPaths(final int rendingChoice) {
		switch (rendingChoice) {
		case UNTAGGED_SKEL:
		case TAGGED_SKEL:
			final ImagePlus imp = renderPathsAsSkeleton(rendingChoice == TAGGED_SKEL);
			if (chosenFile != null)
				imp.setTitle(chosenFile.getName());
			imp.show();
			break;
		case ROI_PATHS:
			final Overlay overlay = new Overlay();
			addPathsToOverlay(overlay, ThreePanes.XY_PLANE, true);
			RoiManager rm = RoiManager.getInstance();
			if (rm == null)
				rm = new RoiManager();
			for (final Roi path : overlay.toArray())
				rm.addRoi(path);
			break;
		case GRAY_3DVIEWER:
		case COLOR_3DVIEWER:
		case COLORMAP_3DVIEWER:
			renderPathsIn3DViewer(rendingChoice, reuseViewer);
			break;
		default:
			IPNAT.handleException(new IllegalArgumentException("Unknown rendering option..."));
			return;
		}
	}

	public boolean isSWCfile(final String filename) {
		return filename.toLowerCase().matches(".*\\.e?swc");
	}

	public boolean isTracesfile(final String filename) {
		return filename.toLowerCase().endsWith(".traces");
	}

	public void applyScalingFactor(final double xScale, final double yScale, final double zScale) {
		if (xScale > 0 && yScale > 0 && zScale > 0) {
			this.xScale = xScale;
			this.yScale = yScale;
			this.zScale = zScale;
		} else {
			this.xScale = DEFAULT_SCALE;
			this.yScale = DEFAULT_SCALE;
			this.zScale = DEFAULT_SCALE;
		}
	}

	public void applyOffset(final double xOffset, final double yOffset, final double zOffset) {
		this.xOffset = xOffset;
		this.yOffset = yOffset;
		this.zOffset = zOffset;
	}

	/** Resets XYZ-offsets of SWC coordinates */
	public void resetOffsets() {
		this.xOffset = DEFAULT_OFFSET;
		this.yOffset = DEFAULT_OFFSET;
		this.zOffset = DEFAULT_OFFSET;
	}
	public void applyCalibration(final double voxelWidth, final double voxelHeight, final double voxelDepth,
			final String unit) {
		this.voxelWidth = voxelWidth;
		this.voxelHeight = voxelHeight;
		this.voxelDepth = voxelDepth;
		this.voxelUnit = unit;
	}

	public void autoLoadSWC(final File file, final boolean replaceAllPaths) {
		chosenFile = file;
		autoLoadSWC(file.getAbsolutePath(), replaceAllPaths);
	}

	public void autoLoadSWC(final String filePath, final boolean replaceAllPaths) {
		applyOffset(xOffset, yOffset, zOffset);
		applyScalingFactor(xScale, yScale, zScale);
		final PathAndFillManager loadingPAFM = new PathAndFillManager(Integer.MAX_VALUE, Integer.MAX_VALUE,
				Integer.MAX_VALUE, 1f, 1f, 1f, null);
		loadingPAFM.importSWC(filePath, assumeCoordinatesIndexVoxels, xOffset, yOffset, zOffset, xScale, yScale, zScale,
				true);
		calculateCanvasDimensions(loadingPAFM);
		loadSWC(filePath, replaceAllPaths);
	}

	/**
	 * Loads a (e)SWC file.
	 *
	 * @param filePath
	 *            the absolute path to the file to be loaded
	 * @param replaceAllPaths
	 *            if {@code true} any existing paths in the
	 *            {@link PathAndFillManager} instance will be replaced by the
	 *            loaded ones.
	 */
	public void loadSWC(final String filePath, final boolean replaceAllPaths) {

		// Allow any type of paths in PathAndFillManager by exaggerating its
		// dimensions. We'll set x,y,z spacing to 1 w/o spatial calibration
		if (pathAndFillManager == null) {
			pathAndFillManager = new PathAndFillManager(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
					(float) voxelWidth, (float) voxelHeight, (float) voxelDepth, voxelUnit);
		}
		pathAndFillManager.importSWC(filePath, assumeCoordinatesIndexVoxels, xOffset, yOffset, zOffset, xScale, yScale,
				zScale, replaceAllPaths);
	}

	/**
	 * Loads a SNT TRACES file (compressed/uncompressed XML).
	 *
	 * @param filePath
	 *            the absolute path to the file to be loaded.
	 */
	public void loadTRACES(final String filePath) {
		pathAndFillManager = PathAndFillManager.createFromTracesFile(filePath);
		tracesFile = true;
	}

	/**
	 * Loads a TRACES or (e)SWC file by guessing its extension.
	 *
	 * @param filePath
	 *            the absolute file path of the file to be imported
	 * @throws IllegalArgumentException
	 *             if file path contains a non-expected extension
	 */
	public void loadGuessingFileType(final String filePath) {
		if (isSWCfile(filePath)) {
			loadSWC(filePath, true);
		} else if (isTracesfile(filePath)) {
			loadTRACES(filePath);
		} else {
			throw new IllegalArgumentException("Cannot load " + filePath);
		}
	}

	/**
	 * Returns a zero-filled image (8-bit) large enough to include all the paths
	 * currently loaded by the plugin.
	 *
	 * @return the rendering canvas
	 * @throws RuntimeException
	 *             if called before successfully loading a file
	 */
	public ImagePlus getRenderingCanvas() {
		initializeTracingCanvas();
		xy = NewImage.createImage("Canvas", width, height, depth, 8, NewImage.FILL_BLACK);
		return xy;
	}

	private void calculateCanvasDimensions(final PathAndFillManager pathAndFillManager) {

		if (pathAndFillManager == null || pathAndFillManager.size() == 0)
			throw new RuntimeException(
					"calculateCanvasDimensions() was called before successfully loading PathAndFillManager");

		// Calculate smallest dimensions of stack holding the rendering of paths
		// and suggest users with suitable offsets in case input was not
		// suitable
		int canvasXstart = Integer.MAX_VALUE;
		int canvasYstart = Integer.MAX_VALUE;
		int canvasZstart = Integer.MAX_VALUE;
		int canvasXend = Integer.MIN_VALUE;
		int canvasYend = Integer.MIN_VALUE;
		int canvasZend = Integer.MIN_VALUE;

		for (int i = 0; i < pathAndFillManager.size(); ++i) {
			final Path p = pathAndFillManager.getPath(i);
			for (int j = 0; j < p.size(); ++j) {
				final int px = p.getXUnscaled(j);
				if (px < canvasXstart)
					canvasXstart = px;
				if (px > canvasXend)
					canvasXend = px;
				final int py = p.getYUnscaled(j);
				if (py < canvasYstart)
					canvasYstart = py;
				if (py > canvasYend)
					canvasYend = py;
				final int pz = p.getZUnscaled(j);
				if (pz < canvasZstart)
					canvasZstart = pz;
				if (pz > canvasZend)
					canvasZend = pz;
			}
		}

		// Calculate n. of slices
		depth = Math.abs(canvasZend - canvasZstart) + 1;
		final boolean twod = depth == 1;

		// Padding is required to accommodate "rounding errors"
		// in PathAndFillManager.setPathPointsInVolume()
		final int xyPadding = 10; // 5 extra pixels on each margin
		final int zPadding = (twod) ? 0 : 2; // 1 slice above first point, 1
												// below last point

		// Set image dimensions
		width = xyPadding + Math.abs(canvasXend - canvasXstart);
		height = xyPadding + Math.abs(canvasYend - canvasYstart);
		if (depth < 1)
			depth = 1;
		if (depth > 1)
			depth += zPadding;

		xOffsetGuessed = -1 * (canvasXstart - xyPadding / 2);
		yOffsetGuessed = -1 * (canvasYstart - xyPadding / 2);
		zOffsetGuessed = -1 * (canvasZstart - zPadding / 2);
		applyOffset(xOffsetGuessed, yOffsetGuessed, zOffsetGuessed);

		if (debug) {
			IPNAT.log(String.format("Calculated offsets: %f, %f, %f", xOffsetGuessed, yOffsetGuessed, zOffsetGuessed));
			IPNAT.log(String.format("Canvas dimensions: %dx%dx%d", width, height, depth));
		}
	}

	private void initializeTracingCanvas() {

		calculateCanvasDimensions(pathAndFillManager);

		// Define spatial calibration of stack. We must initialize
		// stacks.ThreePanes.xy to avoid a NPE later on
		// (tracing.SimpleNeuriteTracer.makePathVolume() inherits
		// stacks.ThreePanes.xy's calibration)
		final Calibration cal = new Calibration();
		if (tracesFile) {
			cal.setUnit(spacing_units);
			cal.pixelWidth = x_spacing;
			cal.pixelHeight = y_spacing;
			cal.pixelDepth = z_spacing;
		} else if (spacing_units != null && !spacing_units.isEmpty()) {
			cal.setUnit(spacing_units);
			cal.pixelWidth = voxelWidth;
			cal.pixelHeight = voxelHeight;
			cal.pixelDepth = voxelHeight;
		}
		if (xy == null)
			xy = new ImagePlus();
		xy.setCalibration(cal);
	}

	/**
	 * Renders imported paths as topographic skeletons.
	 *
	 * @param taggedSkeleton
	 *            If {@code true} {@link AnalyzeSkeleton_} is used to generate a
	 *            tagged skeleton
	 * @throws RuntimeException
	 *             if called before successfully loading a file
	 * @return a tagged/untagged skeleton (8-bit binary image)
	 */
	public ImagePlus renderPathsAsSkeleton(final boolean taggedSkeleton) {
		ImagePlus imp;
		initializeTracingCanvas();
		if (taggedSkeleton) {
			final AnalyzeSkeleton_ as = new AnalyzeSkeleton_();
			as.setup("", makePathVolume());
			as.run();
			final ImageStack stack = as.getResultImage(false);
			imp = new ImagePlus("", stack);
			// ColorMaps.applyMagmaColorMap(imp, 0, false);
			IJ.run(imp, "Fire", null); // Mimic AnalyzeSkeleton_'s default
			imp.resetDisplayRange();
			imp.updateAndDraw();
		} else {
			imp = makePathVolume();
		}
		return imp;
	}

	/**
	 * Retrieves a MIP of skeletonized paths.
	 *
	 * @throws RuntimeException
	 *             if called before successfully loading a file
	 * @return a MIP projection of an untagged skeleton (8-bit binary image)
	 */
	public ImageProcessor getSkeletonizedProjection() {
		final ImagePlus imp = renderPathsAsSkeleton(false);
		final ZProjector zp = new ZProjector(imp);
		zp.setMethod(ZProjector.MAX_METHOD);
		zp.setStartSlice(1);
		zp.setStopSlice(imp.getNSlices());
		zp.doProjection();
		return zp.getProjection().getProcessor();
	}

	private synchronized void renderPathsIn3DViewer(final int choice, final boolean reuseLastViewer) {
		final int nViewers = Image3DUniverse.universes.size();
		boolean reusingViewer = false;
		if (reuseLastViewer && nViewers > 0) {
			univ = Image3DUniverse.universes.get(nViewers - 1);
			reusingViewer = univ != null;
		}
		if (!reusingViewer) {
			univ = createNewUniverse();
		}
		switch (choice) {
		case GRAY_3DVIEWER:
			renderPathsIn3DViewer(DEFAULT_DESELECTED_COLOR, univ);
			break;
		case COLOR_3DVIEWER:
			renderPathsIn3DViewer(DEFAULT_DESELECTED_COLOR, univ);
			break;
		case COLORMAP_3DVIEWER:
			renderPathsIn3DViewer(choice, univ);
			break;
		default:
			throw new RuntimeException("BUG: Choice for 3D rendering was not understood");
		}
		if (!reusingViewer) {
			univ.show();
			final ImageWindow3D window = univ.getWindow();
			if (chosenFile != null)
				window.setTitle(chosenFile.getName());
			GUI.center(window);
		}

	}

	/**
	 * Creates a new 3D Viewer Universe.
	 *
	 * @return the image {@link Image3DUniverse}
	 */
	public Image3DUniverse createNewUniverse() {
		final Image3DUniverse univ = new Image3DUniverse(512, 512);
		univ.setUseToFront(false);
		// univ.addUniverseListener(pathAndFillManager);
		// univ.setAutoAdjustView(false);
		final PointSelectionBehavior psb = new PointSelectionBehavior(univ, this);
		univ.addInteractiveBehavior(psb);
		return univ;
	}

	/**
	 * Renders paths in the 3D viewer.
	 *
	 * @param color
	 *            the color use to render paths
	 * @param univ
	 *            the destination {@link Image3DUniverse}
	 */
	public synchronized void renderPathsIn3DViewer(final Color color, final Image3DUniverse univ) {
		use3DViewer = true;
		this.univ = univ;
		for (int i = 0; i < pathAndFillManager.size(); ++i) {
			final Path p = pathAndFillManager.getPath(i);
			p.addTo3DViewer(univ, color, colorImage);
		}
	}

	/**
	 * Renders paths in the 3D viewer.
	 *
	 * @param choice
	 *            either {@link GRAY_3DVIEWER}, {@link COLOR_3DVIEWER}
	 *            {@link COLORMAP_3DVIEWER}
	 * @param univ
	 *            the destination {@link Image3DUniverse}
	 */
	public synchronized void renderPathsIn3DViewer(final int choice, final Image3DUniverse univ) {
		use3DViewer = true;
		this.univ = univ;

		if (choice == GRAY_3DVIEWER) {
			renderPathsIn3DViewer(DEFAULT_DESELECTED_COLOR, univ);
			return;
		}

		final Color[] colors = new Color[pathAndFillManager.size()];
		if (choice == COLOR_3DVIEWER) {
			for (int i = 0; i < pathAndFillManager.size(); ++i) {
				Color color = pathAndFillManager.getPath(i).getColor();
				if (color == null)
					color = pathAndFillManager.getPath(i).getSWCcolor();
				colors[i] = color;
			}
		} else if (choice == COLORMAP_3DVIEWER) {
			final IndexColorModel cm = ColorMaps.viridisColorMap(-1, false);
			for (int i = 0; i < pathAndFillManager.size(); ++i) {
				final int idx = 255 * i / pathAndFillManager.size();
				colors[i] = new Color(cm.getRed(idx), cm.getGreen(idx), cm.getBlue(idx));
			}
		}

		for (int i = 0; i < pathAndFillManager.size(); ++i) {
			final Path p = pathAndFillManager.getPath(i);
			p.addTo3DViewer(univ, colors[i], colorImage);
		}

	}

	/**
	 * Gets import settings from user.
	 *
	 * The easiest would be to use SWCImportOptionsDialog, however it has
	 * several disadvantages: It is not macro recordable, there isn't an
	 * immediate way to find out if the dialog has been dismissed by the user
	 * and it does not allow for input of spatial calibrations. So we'll have to
	 * re-implement most of it here using IJ's recordable GenericDialog. For
	 * consistency, We will keep the same preference keys used by Simple Neurite
	 * Tracer.
	 *
	 * @return {@code true} if user OKed dialog prompt, otherwise {@code false}
	 */
	private boolean getSWCsettingsFromUser() {
		loadDialogSettings();
		settingsDialog = new EnhancedGenericDialog(chosenFile.getName() + " Rendering");
		settingsDialog.addCheckbox("Apply_spatial calibration to SWC coordinates:", !assumeCoordinatesIndexVoxels);
		settingsDialog.addNumericField("             Voxel_width", voxelWidth, 2);
		settingsDialog.addNumericField("Voxel_height", voxelHeight, 2);
		settingsDialog.addNumericField("Voxel_depth", voxelDepth, 2);
		settingsDialog.addStringField("Unit", voxelUnit, 6);
		settingsDialog.addCheckbox("Apply_offset to SWC coordinates:", applyOffset);
		settingsDialog.addNumericField("X_offset", xOffset, 2);
		settingsDialog.addNumericField("Y_offset", yOffset, 2);
		settingsDialog.addNumericField("Z_offset", zOffset, 2);
		settingsDialog.addCheckbox("Apply_scale to SWC coordinates:", applyScale);
		settingsDialog.addNumericField("X_scale", xScale, 2);
		settingsDialog.addNumericField("Y_scale", yScale, 2);
		settingsDialog.addNumericField("Z_scale", zScale, 2);
		settingsDialog.addMessage(""); // spacer
		settingsDialog.addChoice("Render as:", RENDING_OPTIONS, RENDING_OPTIONS[rendingChoice]);
		settingsDialog.addCheckbox("3D rendering: Re-use existing viewer (if any)", reuseViewer);

		// Add listener anf update prompt
		settingsDialog.addDialogListener(this);
		dialogItemChanged(settingsDialog, null);

		// Add More>> dropdown menu
		final JPopupMenu popup = new JPopupMenu();
		JMenuItem mi;
		mi = new JMenuItem("Restore default options");
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				dialogItemChanged(settingsDialog, e);
			}
		});
		popup.add(mi);
		popup.addSeparator();
		mi = new JMenuItem("About hIPNAT plugins...");
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				dialogItemChanged(settingsDialog, e);
			}
		});
		popup.add(mi);
		settingsDialog.assignPopupToHelpButton(popup);
		settingsDialog.showDialog();

		return settingsDialog.wasOKed();
	}

	private boolean getTracesRendingChoice() {
		settingsDialog = new EnhancedGenericDialog(chosenFile.getName() + " Rendering");
		settingsDialog.addChoice("Render as:", RENDING_OPTIONS, RENDING_OPTIONS[rendingChoice]);
		settingsDialog.addCheckbox("3D rendering: Re-use existing viewer (if any)", reuseViewer);
		settingsDialog.assignListenerToHelpButton("About hIPNAT", new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				IJ.runPlugIn(Help.class.getName(), "");
			}
		});
		settingsDialog.showDialog();
		rendingChoice = settingsDialog.getNextChoiceIndex();
		return settingsDialog.wasOKed();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.gui.DialogListener#dialogItemChanged(ij.gui.GenericDialog,
	 * java.awt.AWTEvent)
	 */
	@Override
	public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {

		if (e != null && e.toString().contains("About")) {
			IJ.runPlugIn(ipnat.Help.class.getName(), "");
			return true;
		} else if (e != null && e.toString().contains("Restore")) {
			resetSWCpreferences();
			loadDialogSettings();
			final Vector<?> checkboxes = gd.getCheckboxes();
			for (int i = 0; i < checkboxes.size(); i++)
				((Checkbox) checkboxes.elementAt(i)).setState(false);
		} else {
			setSWCsettingsFromDialog();
		}
		final Vector<?> nFields = gd.getNumericFields();
		final Vector<?> sFields = gd.getStringFields();
		for (int i = 0; i < 3; i++)
			((Component) nFields.elementAt(i)).setEnabled(!assumeCoordinatesIndexVoxels);
		((Component) sFields.elementAt(0)).setEnabled(!assumeCoordinatesIndexVoxels);
		for (int i = 3; i < 6; i++)
			((Component) nFields.elementAt(i)).setEnabled(applyOffset);
		for (int i = 6; i < 9; i++)
			((Component) nFields.elementAt(i)).setEnabled(applyScale);

		return true;
	}

	private void setSWCsettingsFromDialog() {
		assumeCoordinatesIndexVoxels(!settingsDialog.getNextBoolean());
		voxelWidth = settingsDialog.getNextNumber();
		voxelHeight = settingsDialog.getNextNumber();
		voxelDepth = settingsDialog.getNextNumber();
		voxelUnit = settingsDialog.getNextString();

		applyOffset = settingsDialog.getNextBoolean();
		xOffset = settingsDialog.getNextNumber();
		yOffset = settingsDialog.getNextNumber();
		zOffset = settingsDialog.getNextNumber();

		applyScale = settingsDialog.getNextBoolean();
		xScale = Math.max(0.01, settingsDialog.getNextNumber());
		yScale = Math.max(0.01, settingsDialog.getNextNumber());
		zScale = Math.max(0.01, settingsDialog.getNextNumber());

		rendingChoice = settingsDialog.getNextChoiceIndex();
		reuseViewer = settingsDialog.getNextBoolean();
	}

	private void resetSWCpreferences() {
		Prefs.set(PREFS_KEY + "applyOffset", null);
		Prefs.set(PREFS_KEY + "xOffset", null);
		Prefs.set(PREFS_KEY + "yOffset", null);
		Prefs.set(PREFS_KEY + "zOffset", null);
		Prefs.set(PREFS_KEY + "applyScale", null);
		Prefs.set(PREFS_KEY + "xScale", null);
		Prefs.set(PREFS_KEY + "yScale", null);
		Prefs.set(PREFS_KEY + "zScale", null);
		Prefs.set(PREFS_KEY + "voxelCalibration", null);
	}

	private void loadDialogSettings() {
		final String defaultBoolean = String.valueOf(Boolean.FALSE);
		assumeCoordinatesIndexVoxels(Boolean.parseBoolean(Prefs.get(PREFS_KEY + "ignoreCalibration", defaultBoolean)));
		getCalibrationFromPrefs();
		applyOffset = Boolean.parseBoolean(Prefs.get(PREFS_KEY + "applyOffset", defaultBoolean));
		xOffset = Double.parseDouble(Prefs.get(PREFS_KEY + "xOffset", String.valueOf(DEFAULT_OFFSET)));
		yOffset = Double.parseDouble(Prefs.get(PREFS_KEY + "yOffset", String.valueOf(DEFAULT_OFFSET)));
		zOffset = Double.parseDouble(Prefs.get(PREFS_KEY + "zOffset", String.valueOf(DEFAULT_OFFSET)));
		applyScale = Boolean.parseBoolean(Prefs.get(PREFS_KEY + ".applyScale", defaultBoolean));
		xScale = Double.parseDouble(Prefs.get(PREFS_KEY + "xScale", String.valueOf(DEFAULT_SCALE)));
		yScale = Double.parseDouble(Prefs.get(PREFS_KEY + "yScale", String.valueOf(DEFAULT_SCALE)));
		zScale = Double.parseDouble(Prefs.get(PREFS_KEY + "zScale", String.valueOf(DEFAULT_SCALE)));
	}

	private void getCalibrationFromPrefs() {
		final String defaults = DEF_VOXEL_SIZE + "," + DEF_VOXEL_SIZE + "," + DEF_VOXEL_SIZE + "," + DEF_VOXEL_UNIT;
		try {
			final String[] values = Tools.split(Prefs.get(PREFS_KEY + "voxelCalibration", defaults), ",");
			voxelWidth = Double.parseDouble(values[0]);
			voxelHeight = Double.parseDouble(values[1]);
			voxelDepth = Double.parseDouble(values[2]);
			voxelUnit = values[3];
		} catch (final Exception ignored) {
			applyCalibration(DEF_VOXEL_SIZE, DEF_VOXEL_SIZE, DEF_VOXEL_SIZE, DEF_VOXEL_UNIT);
		}
	}

	private void saveSWCSettings() {
		Prefs.set(PREFS_KEY + "ignoreCalibration", String.valueOf(assumeCoordinatesIndexVoxels));
		Prefs.set(PREFS_KEY + "voxelCalibration", assumeCoordinatesIndexVoxels ? null
				: voxelWidth + "," + voxelHeight + "," + voxelDepth + "," + voxelUnit.replace(",", ""));
		Prefs.set(PREFS_KEY + "applyOffset", String.valueOf(applyOffset));
		Prefs.set(PREFS_KEY + "xOffset", applyOffset ? String.valueOf(xOffset) : null);
		Prefs.set(PREFS_KEY + "yOffset", applyOffset ? String.valueOf(yOffset) : null);
		Prefs.set(PREFS_KEY + "zOffset", applyOffset ? String.valueOf(zOffset) : null);
		Prefs.set(PREFS_KEY + "applyScale", String.valueOf(applyScale));
		Prefs.set(PREFS_KEY + "xScale", applyScale ? String.valueOf(xScale) : null);
		Prefs.set(PREFS_KEY + "yScale", applyScale ? String.valueOf(yScale) : null);
		Prefs.set(PREFS_KEY + "zScale", applyScale ? String.valueOf(zScale) : null);
	}

	public void setDebug(final boolean debug) {
		this.debug = debug;
	}

	public void assumeCoordinatesIndexVoxels(final boolean assumeCoordinatesIndexVoxels) {
		this.assumeCoordinatesIndexVoxels = assumeCoordinatesIndexVoxels;
	}

}
