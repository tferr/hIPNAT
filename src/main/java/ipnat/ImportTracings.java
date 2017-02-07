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
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.YesNoCancelDialog;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.util.Tools;
import ij3d.Image3DUniverse;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sholl.gui.EnhancedGenericDialog;
import stacks.ThreePanes;
import tracing.Path;
import tracing.PathAndFillManager;
import tracing.PointSelectionBehavior;
import tracing.SimpleNeuriteTracer;

// TODO: implement other rending options: ClearVolume
public class ImportTracings extends SimpleNeuriteTracer implements PlugIn, DialogListener {

	/* Default options for swc import */
	final double DEFAULT_OFFSET = 0d;
	final double DEFAULT_SCALE = 1d;
	final float DEFAULT_SPACING = 1f;
	final String DEF_VOXEL_UNIT = "\u00B5m";
	final double DEF_VOXEL_WIDTH = 1d;
	final double DEF_VOXEL_HEIGHT = 1d;
	final double DEF_VOXEL_DEPTH = 1d;

	private final String PREFS_KEY = "tracing.SWCImportOptionsDialog.";
	private EnhancedGenericDialog settingsDialog;

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

	private int rendingChoice = COLOR_3DVIEWER;

	private double xOffset, yOffset, zOffset;
	private double xOffsetGuessed, yOffsetGuessed, zOffsetGuessed;
	private double xScale, yScale, zScale;
	private boolean applyOffset, applyScale, ignoreCalibration;
	private double voxelWidth, voxelHeight, voxelDepth;
	private String voxelUnit;
	private File chosenFile;
	private final boolean guessOffsets = true;
	private boolean tracesFile = false;
	private boolean reuseViewer;

	/** Debugger method */
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
			loadSWCfile(chosenFile.getAbsolutePath());

		} else if (isTracesfile(chosenFile.getName())) {

			if (!getTracesRendingChoice())
				return;
			loadTRACESfile(chosenFile.getAbsolutePath());

		}

		if (pathAndFillManager == null || pathAndFillManager.size() == 0) {
			Utils.error("Invalid File?", "Unable to load tracings from \n" + chosenFile.getAbsolutePath(), null);
			return;
		}

		try {

			switch (rendingChoice) {
			case UNTAGGED_SKEL:
			case TAGGED_SKEL:
				final String impTitle = chosenFile.getName();
				final ImagePlus imp = renderPathVolume(rendingChoice == TAGGED_SKEL);
				imp.setTitle(impTitle);
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
				IJ.log("Bug: Unknown option...");
				return;
			}

		} catch (final Exception e) {

			final String RERUN_FLAG = "rerun";
			if (IJ.macroRunning() || arg.equals(RERUN_FLAG)) {
				IPNAT.handleException(e);
				return;
			}

			if (guessOffsets && chosenFile != null
					&& new YesNoCancelDialog(IJ.getInstance(), "Unable to render " + chosenFile.getName(),
							"Re-try with guessed (presumably more suitable) settings?").yesPressed()) {
				applyScale = false;
				applyOffset = true;
				xOffset = (xOffsetGuessed == 0d) ? 0d : xOffsetGuessed * -1.05;
				yOffset = (yOffsetGuessed == 0d) ? 0d : yOffsetGuessed * -1.05;
				zOffset = (zOffsetGuessed == 0d) ? 0d : zOffsetGuessed * -1.05;
				saveSWCSettings();
				if (Recorder.record) {
					Recorder.setCommand(Recorder.getCommand());
					Recorder.recordPath("open", chosenFile.getAbsolutePath());
				}
				run(RERUN_FLAG);
			}

		}

	}

	public boolean isSWCfile(final String filename) {
		return filename.toLowerCase().matches(".*\\.e?swc"); // .swc and .eswc
																// extensions
	}

	public boolean isTracesfile(final String filename) {
		return filename.toLowerCase().endsWith(".traces");
	}

	public void applyScalingFactor(final double xScale, final double yScale, final double zScale) {
		applyScale = true;
		this.xScale = xScale;
		this.yScale = yScale;
		this.zScale = zScale;
	}

	public void applyOffset(final double xOffset, final double yOffset, final double zOffset) {
		applyOffset = true;
		this.xOffset = xOffset;
		this.yOffset = yOffset;
		this.zOffset = zOffset;
	}

	public void applyCalibration(final double voxelWidth, final double voxelHeight, final double voxelDepth,
			final String voxelUnit) {
		ignoreCalibration = false;
		this.voxelWidth = voxelWidth;
		this.voxelHeight = voxelHeight;
		this.voxelDepth = voxelDepth;
		this.voxelUnit = voxelUnit;
	}

	private void loadSWCfile(final String filename) {
		loadSWCfile(filename, true);
	}

	public void loadSWCfile(final String filename, final boolean replaceAllPaths) {
		// Allow any type of paths in PathAndFillManager by exaggerating its
		// dimensions. We'll set x,y,z spacing to 1 w/o spatial calibration
		if (pathAndFillManager == null) {
			pathAndFillManager = new PathAndFillManager(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 1f, 1f,
					1f, null);
		}
		pathAndFillManager.importSWC(filename, ignoreCalibration, applyOffset ? xOffset : DEFAULT_OFFSET,
				applyOffset ? yOffset : DEFAULT_OFFSET, applyOffset ? zOffset : DEFAULT_OFFSET,
				applyScale ? xScale : DEFAULT_SCALE, applyScale ? yScale : DEFAULT_SCALE,
				applyScale ? zScale : DEFAULT_SCALE, replaceAllPaths);
	}

	private void loadTRACESfile(final String filename) {
		pathAndFillManager = PathAndFillManager.createFromTracesFile(chosenFile.getAbsolutePath());
		tracesFile = true;
	}

	public void loadPathAndFillManager(final String filename) {
		if (isSWCfile(filename)) {
			loadSWCfile(filename);
		} else if (isTracesfile(filename)) {
			loadTRACESfile(filename);
		} else {
			throw new IllegalArgumentException("Cannot load " + filename);
		}
	}

	@Override
	public PathAndFillManager getPathAndFillManager() {
		return pathAndFillManager;
	}

	public ImagePlus getTracingCanvas() {
		if (pathAndFillManager == null || pathAndFillManager.size() == 0)
			throw new RuntimeException("getTracingCanvas() was called before successfully loading PathAndFillManager");
		initializeTracingCanvas();
		return xy;
	}

	private void initializeTracingCanvas() {
		// Calculate smallest dimensions of stack holding the rendering of paths
		// and suggest users with suitable offsets in case input was not
		// suitable
		int cropped_canvas_x = 1;
		int cropped_canvas_y = 1;
		int cropped_canvas_z = 1;
		for (int i = 0; i < pathAndFillManager.size(); ++i) {
			final Path p = pathAndFillManager.getPath(i);
			for (int j = 0; j < p.size(); ++j) {
				cropped_canvas_x = Math.max(cropped_canvas_x, p.getXUnscaled(j));
				cropped_canvas_y = Math.max(cropped_canvas_y, p.getYUnscaled(j));
				cropped_canvas_z = Math.max(cropped_canvas_z, p.getZUnscaled(j));
				if (guessOffsets) {
					xOffsetGuessed = Math.min(xOffsetGuessed, p.getXUnscaled(j));
					yOffsetGuessed = Math.min(yOffsetGuessed, p.getYUnscaled(j));
					zOffsetGuessed = Math.min(zOffsetGuessed, p.getZUnscaled(j));
				}

			}
		}

		// Padding" is essential to accommodate for "rounding"
		// errors in PathAndFillManager.setPathPointsInVolume()
		width = cropped_canvas_x + 10;
		height = cropped_canvas_y + 10;
		depth = (cropped_canvas_z == 1) ? 1 : cropped_canvas_z + 2;

		// Define spatial calibration of stack. We must initialize
		// stacks.ThreePanes.xy to avoid a NPE later on, because
		// tracing.SimpleNeuriteTracer.makePathVolume() inherits
		// stacks.ThreePanes.xy's calibration
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
		xy = new ImagePlus();
		xy.setCalibration(cal);
	}

	public ImagePlus renderPathVolume(final boolean taggedSkeleton) {
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

	public ImageProcessor getRenderedProjection() {
		initializeTracingCanvas();
		final ImagePlus imp = makePathVolume();
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
			univ = getNewUniverse();
		}
		switch (choice) {
		case GRAY_3DVIEWER:
			renderPathsIn3DViewer(DEFAULT_DESELECTED_COLOR, univ);
			break;
		case COLOR_3DVIEWER:
			renderPathsIn3DViewer(DEFAULT_DESELECTED_COLOR, univ);
		}
		renderPathsIn3DViewer(choice, univ);
		if (!reusingViewer) {
			univ.show();
			GUI.center(univ.getWindow());
		}

	}

	public Image3DUniverse getNewUniverse() {
		final Image3DUniverse univ = new Image3DUniverse(512, 512);
		univ.setUseToFront(false);
		// univ.addUniverseListener(pathAndFillManager);
		// univ.setAutoAdjustView(false);
		final PointSelectionBehavior psb = new PointSelectionBehavior(univ, this);
		univ.addInteractiveBehavior(psb);
		return univ;
	}

	public synchronized void renderPathsIn3DViewer(final Color color, final Image3DUniverse univ) {
		use3DViewer = true;
		this.univ = univ;
		for (int i = 0; i < pathAndFillManager.size(); ++i) {
			final Path p = pathAndFillManager.getPath(i);
			p.addTo3DViewer(univ, color, colorImage);
		}
	}

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
	 * and it does not allow for input of spatial calibrations and t. So we'll
	 * have to re-implement most of it here using IJ's recordable GenericDialog.
	 * For consistency, We will keep the same preference keys used by Simple
	 * Neurite Tracer.
	 *
	 * @return {@code true} if user OKed dialog prompt, otherwise {@code false}
	 */
	private boolean getSWCsettingsFromUser() {
		loadDialogSettings();
		settingsDialog = new EnhancedGenericDialog(chosenFile.getName() + " Rendering");
		settingsDialog.addCheckbox("Apply_spatial calibration to SWC coordinates:", !ignoreCalibration);
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
			((Component) nFields.elementAt(i)).setEnabled(!ignoreCalibration);
		((Component) sFields.elementAt(0)).setEnabled(!ignoreCalibration);
		for (int i = 3; i < 6; i++)
			((Component) nFields.elementAt(i)).setEnabled(applyOffset);
		for (int i = 6; i < 9; i++)
			((Component) nFields.elementAt(i)).setEnabled(applyScale);

		return true;
	}

	private void setSWCsettingsFromDialog() {
		ignoreCalibration = !settingsDialog.getNextBoolean();
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
		ignoreCalibration = Boolean.parseBoolean(Prefs.get(PREFS_KEY + "ignoreCalibration", defaultBoolean));
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

	void getCalibrationFromPrefs() {
		final String defaults = DEF_VOXEL_WIDTH + "," + DEF_VOXEL_HEIGHT + "," + DEF_VOXEL_DEPTH + "," + DEF_VOXEL_UNIT;
		try {
			final String[] values = Tools.split(Prefs.get(PREFS_KEY + "voxelCalibration", defaults), ",");
			voxelWidth = Double.parseDouble(values[0]);
			voxelHeight = Double.parseDouble(values[1]);
			voxelDepth = Double.parseDouble(values[2]);
			voxelUnit = values[3];
		} catch (final Exception ignored) {
			voxelWidth = DEF_VOXEL_WIDTH;
			voxelHeight = DEF_VOXEL_HEIGHT;
			voxelDepth = DEF_VOXEL_DEPTH;
			voxelUnit = DEF_VOXEL_UNIT;
		}
	}

	private void saveSWCSettings() {
		Prefs.set(PREFS_KEY + "ignoreCalibration", String.valueOf(ignoreCalibration));
		Prefs.set(PREFS_KEY + "voxelCalibration", ignoreCalibration ? null
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

}
