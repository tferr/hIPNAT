/*
 * #%L
 * hIPNAT plugins for Fiji distribution of ImageJ
 * %%
 * Copyright (C) 2016 Tiago Ferreira
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


import java.awt.Window;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.Menus;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.text.TextWindow;

public class Utils {

	/** Private constructor to prevent class instantiation. */
	private Utils() {
	}

	/**
	 * Returns the ResultsTable of the specified window title (if open) or a new
	 * ResultsTable with appropriated properties (precision of 5 decimal places,
	 * no row numbers, "NaN" padding of empty cells)
	 *
	 * @param title
	 *            the window title of the table
	 * @return a referenced to the opened ResultsTable or a new one if the
	 *         window of the specified title is not associated to a valid
	 *         ResultsTable
	 */
	public static ResultsTable getTable(final String title) {
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

	/**
	 * Macro-friendly error message.
	 *
	 * If a macro is running it will not be aborted and the error message is
	 * displayed in the "Log" window (or the Java console if ImageJ is not
	 * present). Otherwise displays a regular {@link ij.IJ#error(String)
	 * IJ.error()}.
	 *
	 * @param errorTitle
	 *            The error title
	 * @param errorMsg
	 *            The error message
	 * @param imp
	 *            The Image to be mentioned in the message. It is ignored if
	 *            {@code null}
	 */
	public static void error(final String errorTitle, final String errorMsg, final ImagePlus imp) {
		String title = IPNAT.getReadableVersion();
		if (errorTitle != null)
			title = errorTitle + " (" + title + ")";
		final String impMsg = (imp == null) ? "" : "Error while processing " + imp.getTitle();
		if (IJ.macroRunning())
			IJ.log("\n>>> " + title + ": " + impMsg + "\n" + errorMsg);
		else
			IJ.error(title, impMsg + "\n" + errorMsg);
	}

	public static boolean validSkelDependencies() {
		return classExists(
				Arrays.asList("sc.fiji.analyzeSkeleton.AnalyzeSkeleton_", "sc.fiji.skeletonize3D.Skeletonize3D_"));
	}

	public static boolean classExists(final List<String> classStringNames) {
		if (classStringNames != null) {
			for (final String cls : classStringNames) {
				try {
					Class.forName(cls);
				} catch (final ClassNotFoundException e) {
					IPNAT.handleException(e);
					return false;
				}
			}
		}
		return true;
	}

	public boolean classExists(final String classStringName) {
		return classExists(Collections.singletonList(classStringName));
	}

	public boolean commandExists(final List<String> commandLabels) {
		@SuppressWarnings("rawtypes")
		final Hashtable commands = Menus.getCommands();
		for (final String commandLabel : commandLabels) {
			if (commands.get(commandLabel) == null)
				return false;
		}
		return true;
	}

	public boolean commandExists(final String commandLabel) {
		return commandExists(Collections.singletonList(commandLabel));
	}

}
