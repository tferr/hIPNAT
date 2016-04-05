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


import java.io.CharArrayWriter;
import java.io.PrintWriter;

import ij.IJ;
import ij.text.TextWindow;

public class ExceptionHandler implements IJ.ExceptionHandler {

	String CLASS_NOT_FOUND = "A Java file was not found";
	String METHOD_NOT_FOUND = "Your IJ installation is likeley outdated";
	String UNNAMED_ERROR = "An error occured";
	String TIPS = "Troubleshooting tips:\n"
			+ "  - Ensure you are subscribed to the Fiji update site\n"
			+ "  - Run the updater to install missing files or update deprecated ones\n"
			+ "  - Useful resources:\n"
			+ "    - http://imagej.net/Troubleshooting\n"
			+ "    - http://forum.imagej.net/\n"
			+ "    - http://imagej.net/Frequently_Asked_Questions";

	@Override
	public void handle(final Throwable t) {
		final CharArrayWriter writer = new CharArrayWriter();
		final PrintWriter pw = new PrintWriter(writer);
		t.printStackTrace(pw);
		final String trace = writer.toString();
		String tMsg;
		switch (trace.substring(0, trace.indexOf(":"))) {
		case "java.lang.ClassNotFoundException":
			tMsg = CLASS_NOT_FOUND;
			break;
		case "java.lang.NoSuchMethodException":
			tMsg = METHOD_NOT_FOUND;
			break;
		default:
			tMsg = UNNAMED_ERROR;
		}
		tMsg += " (details below). " + TIPS + "\n \n" + trace;
		if (IJ.getInstance() != null) {
			new TextWindow("Exception", IPNAT.getReadableVersion(), tMsg, 500, 250);
		} else
			IJ.log(tMsg);
	}

}